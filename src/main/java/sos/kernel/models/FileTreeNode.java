package sos.kernel.models;

import java.util.ArrayList;

public class FileTreeNode {
    public enum FileType {
        FILE,
        DIRECTORY,
        DEVICES,
        SYMBOLIC_LINK
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

    private final Object contentLock = new Object();
    public String FilePath = "";

    public String readContents() {
        synchronized (contentLock) {
            return this.contents;
        }
    }

    public void writeContents(String append) {
        synchronized(contentLock) {
            if(append == null)
                this.contents = "";
            else
                this.contents =  append;
        }
    }
    public void appendContents(String append) {
        synchronized(contentLock) {
            this.contents = this.contents + append;
        }
    }

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
