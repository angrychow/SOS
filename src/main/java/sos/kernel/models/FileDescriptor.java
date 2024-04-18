package sos.kernel.models;

import com.alibaba.fastjson2.annotation.JSONField;

public class FileDescriptor {
    public int PCBID;
    public int FDID;
    @JSONField(serialize = false)
    public FileTreeNode FileNode;
    public boolean readable;
    public boolean writable;
    public int cursor;

    @Override
    public String toString() {
        return "FileDescriptor{" +
                "PCBID=" + PCBID +
                ", FDID=" + FDID +
                ", readable=" + readable +
                ", writable=" + writable +
                ", cursor=" + cursor +
                '}';
    }
}
