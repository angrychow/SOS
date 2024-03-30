package sos.kernel.models;

public class FileDescriptor {
    public int PCBID;
    public int FDID;
    public FileTreeNode FileNode;
    public boolean readable;
    public boolean writable;
    public int cursor;
}
