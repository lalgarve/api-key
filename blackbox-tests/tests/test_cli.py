import pytest

from blackbox.cli import (FIXED_VARIABLES, CliResult, JarNotFound, child_environment, find_jar,
                          java_executable, mask_keys)

KEY = "dak_" + "a1B2-_" * 7 + "x"


def test_find_jar_uses_api_key_cli_jar_when_set(tmp_path):
    jar = tmp_path / "custom.jar"
    jar.write_text("")
    assert find_jar({"API_KEY_CLI_JAR": str(jar)}, target_dir=tmp_path / "unused") == jar


def test_find_jar_rejects_a_missing_explicit_jar(tmp_path):
    with pytest.raises(JarNotFound, match="not a file"):
        find_jar({"API_KEY_CLI_JAR": str(tmp_path / "missing.jar")})


def test_find_jar_ignores_the_original_backup(tmp_path):
    (tmp_path / "api-key-1.0.jar").write_text("")
    (tmp_path / "api-key-1.0.jar.original").write_text("")
    assert find_jar({}, target_dir=tmp_path) == tmp_path / "api-key-1.0.jar"


def test_find_jar_asks_for_mvn_package_when_there_is_no_jar(tmp_path):
    with pytest.raises(JarNotFound, match="mvn package"):
        find_jar({}, target_dir=tmp_path)


def test_child_environment_keeps_only_allowed_variables():
    env = child_environment({
        "PATH": "/bin", "JAVA_HOME": "/jdk", "SPRING_PROFILES_ACTIVE": "docker",
        "SPRING_DATASOURCE_URL": "jdbc:x", "JAVA_TOOL_OPTIONS": "-Dproxy", "HOME": "/root",
        "API_KEY_HMAC_PEPPER": "real-secret"})
    assert env == {"PATH": "/bin", "JAVA_HOME": "/jdk", "SPRING_PROFILES_ACTIVE": "docker",
                   "SPRING_DATASOURCE_URL": "jdbc:x", **FIXED_VARIABLES}


def test_child_environment_applies_overrides_then_removals():
    env = child_environment({}, overrides={"SPRING_PROFILES_ACTIVE": "staging"},
                            removed={"API_KEY_HMAC_PEPPER"})
    assert env["SPRING_PROFILES_ACTIVE"] == "staging"
    assert "API_KEY_HMAC_PEPPER" not in env


def test_java_executable_prefers_java_home():
    assert java_executable({"JAVA_HOME": "/jdk"}) == "/jdk/bin/java"
    assert java_executable({}) == "java"


def test_mask_keys_hides_every_key():
    assert mask_keys(f"a {KEY} b {KEY}") == "a dak_**** b dak_****"


def test_result_report_has_everything_but_the_key():
    result = CliResult(("java", "-jar", "x.jar", "generate"), 3, f"{KEY}\n", "Error: boom\n")
    report = str(result)
    assert "command: java -jar x.jar generate" in report
    assert "exit code: 3" in report
    assert "Error: boom" in report
    assert KEY not in report
    assert "dak_****" in report
