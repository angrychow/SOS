package sos.kernel.models;

public class PageEntry {
    public boolean Valid;
    public boolean Dirty;
    public int PhyPage;

    public PageEntry(boolean valid, boolean dirty, int phyPage) {
        Valid = valid;
        Dirty = dirty;
        PhyPage = phyPage;
    }
}
