// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PluginSearchTextField extends SearchTextField {
  private boolean mySkipDocumentEvents;

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x - 1, y, width + 2, height);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.height = JBUI.scale(38);
    return size;
  }

  public boolean isSkipDocumentEvents() {
    return mySkipDocumentEvents;
  }

  public void setTextIgnoreEvents(@NotNull String text) {
    try {
      mySkipDocumentEvents = true;
      setText(text);
    }
    finally {
      mySkipDocumentEvents = false;
    }
  }
}