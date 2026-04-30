"""Python info compatibility stub for Java-focused Bazel JDT Bridge.

Always returns no Python info found, allowing the aspect to skip Python
collection code paths. This avoids a dependency on @rules_python.

Diverges from upstream bazelbuild/intellij/aspect/python_info.bzl which
loads from @rules_python//python:defs.bzl.
"""

def py_info_in_target(target):
    """Returns False — no Python provider detection in Java-focused mode."""
    return False

def get_py_info(target):
    """Returns None — no Python info available in Java-focused mode."""
    return None
