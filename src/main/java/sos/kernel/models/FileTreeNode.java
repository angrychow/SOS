package sos.kernel.models;

import java.util.ArrayList;

public class FileTreeNode {
    public enum FileType {
        FILE,
        DIRECTORY,
        DEVICES,
    }
    public FileTreeNode() {
        Sons = new ArrayList<>();
        DeviceName = "";
    }
    public ArrayList<FileTreeNode> Sons;
    public FileType Type;
    public String DeviceName;
    public String Name;
    public FileDescriptor Link;
    public String contents = "";

    @Override
    public String toString() {
        return "FileTreeNode{" +
                "Sons=" + Sons +
                ", Type=" + Type +
                ", DeviceName='" + DeviceName + '\'' +
                ", Name='" + Name + '\'' +
                ", Link=" + Link +
                ", contents='" + contents + '\'' +
                '}';
    }
}