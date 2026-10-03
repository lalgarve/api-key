"""Per-scenario state: unique client names, keys captured from generate, and their ids."""

import re
import shlex
import uuid

from blackbox.cli import KEY_PATTERN, child_environment, run_cli
from blackbox.list_table import parse_list_output

CLIENT_PLACEHOLDER = re.compile(r"\{([A-Za-z0-9_-]+)\}")
ID_PLACEHOLDER = re.compile(r"\{id:([^}]+)\}")


class ScenarioState:
    def __init__(self, jar):
        self.jar = jar
        self.suffix = uuid.uuid4().hex[:8]
        self.keys = {}            # label -> plaintext key, only in memory
        self.labels_by_client = {}  # client -> labels in generation order
        self.ids = {}             # label -> id, filled lazily from `list`
        self.env_overrides = {}
        self.env_removed = set()
        self.last = None

    def client(self, name):
        return f"{name}-{self.suffix}"

    def expand(self, text):
        """Replaces {id:label} with the key's id and {name} with this scenario's client name."""
        text = ID_PLACEHOLDER.sub(lambda m: str(self.key_id(m.group(1))), text)
        return CLIENT_PLACEHOLDER.sub(lambda m: self.client(m.group(1)), text)

    def run(self, args, stdin=""):
        env = child_environment(overrides=self.env_overrides, removed=self.env_removed)
        return run_cli(self.jar, args, stdin=stdin, env=env)

    def run_command_line(self, command_line, stdin=""):
        self.last = self.run(shlex.split(self.expand(command_line)), stdin=stdin)
        return self.last

    def run_setup(self, args):
        """A Given step's command: it must succeed, or the scenario's premise is false."""
        result = self.run(args)
        assert result.exit_code == 0, f"setup command failed:\n{result}"
        return result

    def generate(self, name, label, extra_args=()):
        client = self.client(name)
        result = self.run_setup(["generate", "--client", client, *extra_args])
        keys = KEY_PATTERN.findall(result.stdout)
        assert len(keys) == 1, f"expected one key on stdout of generate:\n{result}"
        self.keys[label] = keys[0]
        self.labels_by_client.setdefault(client, []).append(label)
        return result

    def list_client(self, client):
        result = self.run_setup(["list", "--client", client, "--status", "all"])
        return parse_list_output(result.stdout)

    def key_id(self, label):
        """Ids come from `list`: the client is unique to the scenario and ids grow with each
        generate, so the n-th key generated for a client is the n-th lowest id."""
        if label not in self.ids:
            client = next((c for c, labels in self.labels_by_client.items() if label in labels), None)
            assert client is not None, f"no key labeled {label!r} was generated in this scenario"
            rows = sorted(self.list_client(client), key=lambda row: int(row["ID"]))
            labels = self.labels_by_client[client]
            assert len(rows) >= len(labels), f"list shows fewer keys than were generated for {client}"
            for row_label, row in zip(labels, rows):
                self.ids[row_label] = int(row["ID"])
        return self.ids[label]

    def row_for(self, label):
        key_id = self.key_id(label)
        client = next(c for c, labels in self.labels_by_client.items() if label in labels)
        return next(row for row in self.list_client(client) if int(row["ID"]) == key_id)
