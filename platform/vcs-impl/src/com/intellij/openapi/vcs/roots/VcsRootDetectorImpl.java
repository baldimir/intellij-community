/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.vfs.VirtualFileVisitor.CONTINUE;

public class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsRootChecker[] myCheckers;

  @Nullable private Collection<VcsRoot> myDetectedRoots;
  @NotNull private final Object LOCK = new Object();

  public VcsRootDetectorImpl(@NotNull Project project,
                             @NotNull ProjectRootManager projectRootManager,
                             @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myProject = project;
    myProjectManager = projectRootManager;
    myVcsManager = projectLevelVcsManager;
    myCheckers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect() {
    synchronized (LOCK) {
      myDetectedRoots = detect(myProject.getBaseDir());
      return myDetectedRoots;
    }
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    return doDetect(startDir);
  }

  @NotNull
  private Collection<VcsRoot> doDetect(@Nullable VirtualFile startDir) {
    if (startDir == null || myCheckers.length == 0) {
      return Collections.emptyList();
    }

    Set<VcsRoot> roots = scanForRootsInsideDir(startDir);
    if (shouldScanAbove(startDir, roots)) {
      VcsRoot rootAbove = scanForSingleRootAboveDir(startDir);
      if (rootAbove != null) roots.add(rootAbove);
    }
    roots.addAll(scanForRootsInContentRoots());
    return Collections.unmodifiableSet(roots);
  }

  @Override
  @NotNull
  public Collection<VcsRoot> getOrDetect() {
    synchronized (LOCK) {
      if (myDetectedRoots == null) {
        detect();
      }
      return myDetectedRoots;
    }
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInContentRoots() {
    Set<VcsRoot> vcsRoots = new HashSet<>();
    if (myProject.isDisposed()) return vcsRoots;

    for (VirtualFile contentRoot : myProjectManager.getContentRoots()) {
      if (myProject.getBaseDir() != null && VfsUtilCore.isAncestor(myProject.getBaseDir(), contentRoot, false)) {
        continue;
      }

      Set<VcsRoot> vcsRootsInContentRoot = scanForRootsInsideDir(contentRoot);
      if (shouldScanAbove(contentRoot, vcsRootsInContentRoot)) {
        VcsRoot rootAbove = scanForSingleRootAboveDir(contentRoot);
        if (rootAbove != null) vcsRootsInContentRoot.add(rootAbove);
      }
      vcsRoots.addAll(vcsRootsInContentRoot);
    }
    return vcsRoots;
  }

  private Set<VcsRoot> scanForRootsInsideDir(@NotNull VirtualFile root) {
    Set<VcsRoot> roots = new HashSet<>();
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(myProject, myProjectManager, root, dir -> {
      AbstractVcs vcs = getVcsFor(dir);
      if (vcs != null) {
        LOG.debug("Found VCS " + vcs + " in " + dir);
        roots.add(new VcsRoot(vcs, dir));
      }
      return CONTINUE;
    });
    return roots;
  }

  private static boolean shouldScanAbove(@NotNull VirtualFile startDir, @NotNull Set<VcsRoot> rootsInsideDir) {
    return rootsInsideDir.stream().noneMatch(it -> startDir.equals(it.getPath()));
  }

  @Nullable
  private VcsRoot scanForSingleRootAboveDir(@NotNull final VirtualFile dir) {
    if (myProject.isDisposed()) {
      return null;
    }

    ProgressManager.checkCanceled();
    VirtualFile par = dir.getParent();
    while (par != null && !par.equals(VfsUtil.getUserHomeDir())) {
      AbstractVcs vcs = getVcsFor(par, dir);
      if (vcs != null) return new VcsRoot(vcs, par);
      par = par.getParent();
    }
    return null;
  }

  @Nullable
  private AbstractVcs getVcsFor(@NotNull VirtualFile dir) {
    return getVcsFor(dir, null);
  }

  @Nullable
  private AbstractVcs getVcsFor(@NotNull VirtualFile maybeRoot, @Nullable VirtualFile dirToCheckForIgnore) {
    String path = maybeRoot.getPath();
    for (VcsRootChecker checker : myCheckers) {
      if (checker.isRoot(path) && (dirToCheckForIgnore == null || !checker.isIgnored(maybeRoot, dirToCheckForIgnore))) {
        return myVcsManager.findVcsByName(checker.getSupportedVcs().getName());
      }
    }
    return null;
  }
}
