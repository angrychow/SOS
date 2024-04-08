package sos.kernel.models;

import java.util.ArrayList;
import java.util.Map;

public class SOSInfo {
    public ArrayList<PCB> pcbList;
    public PCB nowProcess;
    public Map<String, Map<String, PageEntry>> pages;
}
