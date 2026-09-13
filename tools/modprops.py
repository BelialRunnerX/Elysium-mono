"""
Reading a module's gradle.properties, and expanding a template against it.

Gradle's `processResources` expands `${mod_id}` and friends in
neoforge.mods.toml from the project properties. Nothing outside Gradle knows how
to do that, which is a problem for any tool that has to look at a mod's real
metadata without a twenty-minute NeoGradle build in front of it — and in this
environment Gradle cannot reach the NeoForge repositories at all.

So this re-implements exactly the part that matters: read the properties file,
substitute `${name}`, and refuse loudly on a placeholder nothing defines. That
last part is the reason this is a module rather than three lines of regex
inline. A `${mod_verison}` typo expands to the empty string under a permissive
substitution, which produces a mods.toml that parses, loads, and declares a mod
with no version.
"""
import os
import re

PLACEHOLDER = re.compile(r"\$\{([A-Za-z0-9_.]+)\}")


class MissingProperty(KeyError):
    """A template names something gradle.properties does not define."""


def read_properties(path):
    """
    A .properties file as a dict.

    Deliberately minimal: `key=value`, `#` comments, no line continuations and
    no escape processing. That is all these files use, and a full
    java.util.Properties parser here would be more code pretending to more
    fidelity than it has.
    """
    values = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("!"):
                continue
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()
    return values


def module_properties(module_dir):
    """The properties Gradle would expand a template with, for one module."""
    return read_properties(os.path.join(module_dir, "gradle.properties"))


def expand(template, properties, where="<template>"):
    """
    Substitute every ${name}, or raise.

    Raising rather than leaving the placeholder in place is the whole point.
    An unexpanded `${mod_id}` in a shipped mods.toml is a mod whose id is
    literally "${mod_id}", which loads, appears on the mods screen, and fails in
    a way nobody traces back to a build script.
    """
    missing = []

    def replace(match):
        name = match.group(1)
        if name not in properties:
            missing.append(name)
            return match.group(0)
        return properties[name]

    result = PLACEHOLDER.sub(replace, template)
    if missing:
        raise MissingProperty(
            f"{where}: no value for {sorted(set(missing))} — "
            f"gradle.properties defines {sorted(properties)}")
    return result


def expand_file(template_path, properties):
    with open(template_path, encoding="utf-8") as handle:
        return expand(handle.read(), properties, where=template_path)
