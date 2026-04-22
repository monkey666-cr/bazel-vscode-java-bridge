package com.bazel.jdt;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager;
import org.eclipse.jdt.ls.core.AbstractProjectImporter;

import com.bazel.jdt.BazelBridge;
import com.bazel.jdt.BazelClasspathContainer;
import com.bazel.jdt.BazelClasspathManager;

public class BazelProjectImporter extends AbstractProjectImporter {
    private File rootFolder;

    @Override
    public void initialize(File rootFolder) {
        this.rootFolder = rootFolder;
    }

    @Override
    public boolean applies(IProgressMonitor monitor) {
        if (rootFolder == null) return false;
        return new File(rootFolder, "WORKSPACE").exists()
                || new File(rootFolder, "WORKSPACE.bazel").exists();
    }

    @Override
    public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
        String workspacePath = rootFolder.getAbsolutePath();
        String bazelPath = "bazel";
        String cacheDir = System.getProperty("user.home", "") + "/.cache/bazel-jdt";

        BazelBridge bridge = BazelBridge.getInstance();
        bridge.initialize(workspacePath, bazelPath, cacheDir);

        String[] targets;
        try {
            targets = bridge.discoverTargets();
        } catch (Exception e) {
            throw new CoreException(
                org.eclipse.core.runtime.Status.error("Failed to discover targets", e),
                "Failed to discover Bazel targets: " + e.getMessage()
            );
        }

        if (targets == null || targets.length == 0) return;

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspaceRoot();

        List<IProject> createdProjects = new ArrayList<>();
        for (String targetLabel : targets) {
            try {
                String packageName = extractPackageName(targetLabel);
                IProject project = createEclipseProject(workspace, workspaceRoot, packageName, targetLabel);
                if (project != null) {
                    createdProjects.add(project);
                    BazelClasspathManager.setClasspathContainer(project, targetLabel);
                }
            } catch (Exception e) {
            }
        }
    }

    private String extractPackageName(String targetLabel) {
        int colonIndex = targetLabel.lastIndexOf(':');
        if (colonIndex > 2) {
            return targetLabel.substring(2, colonIndex);
        }
        return targetLabel.substring(2);
    }

    private IProject createEclipseProject(IWorkspace workspace, IWorkspaceRoot workspaceRoot, String packageName, String targetLabel) throws CoreException {
        IProjectDescription description = workspace.newProjectDescription(packageName);
        IProject project = workspaceRoot.getProject(description);
        return project;
    }
}
