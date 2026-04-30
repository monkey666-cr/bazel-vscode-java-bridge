"""CcInfo compatibility layer for Java-focused Bazel JDT Bridge.

Provides stub CcInfo/CcToolchainInfo providers that no real target will match,
causing all C++ collection code paths in intellij_info_impl_bundled.bzl to
short-circuit and return False. This effectively disables C++/Python IDE info
collection while keeping the aspect parseable.

Diverges from upstream bazelbuild/intellij/aspect/cc_info.bzl which loads
from @rules_cc//cc:defs.bzl. That approach requires @rules_cc to be configured
in the workspace, which is not guaranteed for Java-only projects.
"""

# TEMPLATE-IGNORE-BEGIN
CC_USE_GET_TOOL_FOR_ACTION = True
# TEMPLATE-IGNORE-END

# TEMPLATE-INCLUDE-BEGIN
##CC_USE_GET_TOOL_FOR_ACTION = ${use_get_tool_for_action}
# TEMPLATE-INCLUDE-END

# Stub providers that no real Bazel target provides. When the aspect checks
# "if CcInfoCompat in target:" or "if CcInfoCompat not in target:", it will
# always take the False/True branch respectively, skipping C++ collection.
CcInfoCompat = provider(doc = "Stub CcInfo for Java-focused mode")

# Must be a top-level assignment so Bazel recognizes it as an exported provider
# (required for the "in target" / target[Provider] query pattern).
CcToolchainInfoCompat = provider(doc = "Stub CcToolchainInfo for Java-focused mode")

cc_common_compat = struct(
    CcToolchainInfo = CcToolchainInfoCompat,
)
