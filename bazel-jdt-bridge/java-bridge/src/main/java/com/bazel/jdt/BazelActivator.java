package com.bazel.jdt;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class BazelActivator implements BundleActivator {
    private static final ILog LOG = Platform.getLog(BazelActivator.class);

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.log(new Status(IStatus.INFO, "com.bazel.jdt",
            "Bazel JDT Bridge bundle starting"));
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.log(new Status(IStatus.INFO, "com.bazel.jdt",
            "Bazel JDT Bridge bundle stopping"));
        BazelBridge.getInstance().shutdown();
    }
}
