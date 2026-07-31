package com.timbernest.cam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Per-job CAM overrides (disabled screws, feature toggles). Stored as JSON on the job. */
public class CamOptions {
    private List<String> disabledFixingIds = new ArrayList<>();
    private Boolean tabsEnabled;
    private Boolean fixingsEnabled;

    public List<String> getDisabledFixingIds() { return disabledFixingIds; }
    public void setDisabledFixingIds(List<String> disabledFixingIds) {
        this.disabledFixingIds = disabledFixingIds != null ? disabledFixingIds : new ArrayList<>();
    }
    public Boolean getTabsEnabled() { return tabsEnabled; }
    public void setTabsEnabled(Boolean tabsEnabled) { this.tabsEnabled = tabsEnabled; }
    public Boolean getFixingsEnabled() { return fixingsEnabled; }
    public void setFixingsEnabled(Boolean fixingsEnabled) { this.fixingsEnabled = fixingsEnabled; }

    public Set<String> disabledSet() {
        return new HashSet<>(disabledFixingIds == null ? List.of() : disabledFixingIds);
    }
}
