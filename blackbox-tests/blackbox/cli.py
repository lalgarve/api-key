"""Runs the packaged CLI jar as a child process and captures exactly what it prints."""

import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_TARGET_DIR = REPO_ROOT / "api-key-cli" / "target"

# Same shape as ApiKeyFormat in api-key-core: "dak_" + 43 base64url characters.
KEY_PATTERN = re.compile(r"dak_[A-Za-z0-9_-]{43}")
MASKED_KEY = "dak_****"

TEST_PEPPER = "blackbox-test-pepper-not-a-secret"
TIMEOUT_SECONDS = 120

# Only these reach the child process from the caller's environment; everything else (notably
# JAVA_TOOL_OPTIONS, whose "Picked up ..." notice would land on stderr) is left out.
INHERITED_NAMES = ("PATH", "JAVA_HOME", "SPRING_PROFILES_ACTIVE")
INHERITED_PREFIXES = ("SPRING_DATASOURCE_",)

# Spring Boot prints its banner and INFO logs on stdout; the JVM replaces non-ASCII output
# with "?" when no locale is set. See specs/012-blackbox-cli-tests/plan.md.
FIXED_VARIABLES = {
    "LANG": "C.UTF-8",
    "SPRING_MAIN_BANNER_MODE": "off",
    "LOGGING_LEVEL_ROOT": "ERROR",
    "API_KEY_HMAC_PEPPER": TEST_PEPPER,
}


class JarNotFound(Exception):
    pass


def find_jar(environ=None, target_dir=DEFAULT_TARGET_DIR):
    environ = os.environ if environ is None else environ
    explicit = environ.get("API_KEY_CLI_JAR")
    if explicit:
        jar = Path(explicit)
        if not jar.is_file():
            raise JarNotFound(f"API_KEY_CLI_JAR points to {jar}, which is not a file.")
        return jar
    # The repackaged jar ends in ".jar"; Spring Boot's backup of the plain one ends in
    # ".jar.original", so the glob already leaves it out.
    jars = sorted(Path(target_dir).glob("api-key-*.jar"))
    if len(jars) != 1:
        found = ", ".join(str(j) for j in jars) or "none"
        raise JarNotFound(
            f"Expected exactly one api-key-*.jar in {target_dir} (found: {found}). "
            "Run 'mvn package' first, or set API_KEY_CLI_JAR.")
    return jars[0]


def child_environment(environ=None, overrides=None, removed=()):
    environ = os.environ if environ is None else environ
    env = {name: value for name, value in environ.items()
           if name in INHERITED_NAMES or name.startswith(INHERITED_PREFIXES)}
    env.update(FIXED_VARIABLES)
    env.update(overrides or {})
    for name in removed:
        env.pop(name, None)
    return env


def mask_keys(text):
    return KEY_PATTERN.sub(MASKED_KEY, text)


@dataclass(frozen=True)
class CliResult:
    args: tuple
    exit_code: int
    stdout: str
    stderr: str

    def __str__(self):
        return mask_keys(
            f"command: {' '.join(self.args)}\n"
            f"exit code: {self.exit_code}\n"
            f"--- stdout ---\n{self.stdout}"
            f"--- stderr ---\n{self.stderr}"
            f"--- end ---")


def java_executable(env):
    java_home = env.get("JAVA_HOME")
    return str(Path(java_home) / "bin" / "java") if java_home else "java"


def run_cli(jar, args, stdin="", env=None):
    """Runs one CLI command. stdin is always closed after writing, so nothing waits on it."""
    env = child_environment() if env is None else env
    command = (java_executable(env), "-jar", str(jar), *args)
    completed = subprocess.run(
        command, input=stdin, capture_output=True, text=True, encoding="utf-8",
        timeout=TIMEOUT_SECONDS, env=env, check=False)
    return CliResult(command, completed.returncode, completed.stdout, completed.stderr)
