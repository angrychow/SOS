package sos.kernel.models;

public class SwappedOutPage {
    public int PCBID;
    public int VirPage;
    public Object[] PageContents;

    public SwappedOutPage(int PCBID, int virPage, Object[] pageContents) {
        this.PCBID = PCBID;
        VirPage = virPage;
        PageContents = pageContents;
    }
}
