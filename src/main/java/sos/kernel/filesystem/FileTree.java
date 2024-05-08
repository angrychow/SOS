package sos.kernel.filesystem;

import sos.kernel.models.*;

import java.util.ArrayList;
import java.util.Arrays;

//TODO:link function
public class FileTree {
    public FileTreeNode Root;
    public ArrayList<FileDescriptor> FDTable;
    public int FDID = 0;
    public InterruptVector interruptVector;

    public FileTree(InterruptVector v) {
        Root = new FileTreeNode();
        FDTable = new ArrayList<>();
        Root.Type = FileTreeNode.FileType.DIRECTORY;
        Root.Name = "root_";
        var Root_ = new FileTreeNode();
        Root_.Name = "root";
        Root_.FilePath = "root";
        var Dev = new FileTreeNode();
        Dev.Name = "dev";
        Dev.Type = FileTreeNode.FileType.DIRECTORY;
        Dev.FilePath = "root/dev";
        var Std = new FileTreeNode();
        Std.DeviceName = "std";
        Std.Name = "std";
        Std.Type = FileTreeNode.FileType.DEVICES;
        Root_.Type = FileTreeNode.FileType.DIRECTORY;
        Root.Sons.add(Root_);
        Root_.Sons.add(Dev);
        this.CreateFile("root/dev", Std);
        interruptVector = v;
    }

    public FileTreeNode FoundFile(String FilePath) {
        var list = new ArrayList<>(Arrays.asList(FilePath.split("/")));
        return FoundFile_(list, Root);
    }

    private FileTreeNode FoundFile_(ArrayList<String> FilePath, FileTreeNode Node) {//找目录
        if (FilePath.isEmpty()) {
            return Node;//查到最底层了，返回
        }
        var name = FilePath.getFirst();
        FilePath.removeFirst();
        if (Node.Type != FileTreeNode.FileType.DIRECTORY) {
            return null;
        }
        FileTreeNode n = null;
        for (var son : Node.Sons) {
            if (son.Name.equals(name)) {
                n = son;//在儿子里找符合名字的目录
            }
        }
        if (n == null) return null;
        return FoundFile_(FilePath, n);//递归查找
    }

    public boolean CreateFile(String FilePath, FileTreeNode Node) {
        var list = new ArrayList<>(Arrays.asList(FilePath.split("/")));
        var prt = FoundFile_(list, Root);//找到父目录
        if (prt == null) return false;
        if (prt.Type != FileTreeNode.FileType.DIRECTORY) return false;
        prt.Sons.add(Node);
        Node.FilePath = FilePath + '/' + Node.Name;
        return true;
    }

    public boolean DeleteFile(String FilePath) {
        var list = new ArrayList<>(Arrays.asList(FilePath.split("/")));
        var name = list.removeLast();
        var prt = FoundFile_(list, Root);
        if (prt == null) return false;
        if (prt.Type != FileTreeNode.FileType.DIRECTORY) return false;
        FileTreeNode d = null;
        for (var node : prt.Sons) {
            if (node.Name.equals(name)) {
                d = node;
            }
        }
        if (d == null) return false;
        if (d.Link != null) return false;
        return prt.Sons.remove(d);
    }

    public FileDescriptor OpenFile(String FilePath, PCB process, boolean readable, boolean writable, int cursor) {
        var node = FoundFile(FilePath);
        if (node.Type == FileTreeNode.FileType.SYMBOLIC_LINK) {
            node = FoundFile(node.contents);
        }
        if (node.Link != null) return null;
        node.Link = new FileDescriptor();
        node.Link.FileNode = node;
        node.Link.PCBID = process.PCBID;
        node.Link.readable = readable;
        node.Link.writable = writable;
        node.Link.cursor = cursor;
        node.Link.FDID = ++FDID;
        FDTable.add(node.Link);
        return node.Link;
    }

    public boolean CloseFile(FileDescriptor FD) {
        FD.FileNode.Link = null;
        return FDTable.remove(FD);
    }

    public void ReadFile(FileDescriptor FD, int size, int addr, PCB process) {
        interruptVector.RWQueue.add(new RWInterrupt(process, FD.FileNode, 0, size, 0, RWInterrupt.RWType.READ, addr));
        process.ProcessState = PCB.State.WAITING;
    }

    public void WriteFile(FileDescriptor FD, int contentsAddr, PCB process) {
        interruptVector.RWQueue.add(new RWInterrupt(process, FD.FileNode, FD.cursor, 0, contentsAddr, RWInterrupt.RWType.WRITE, 0));
        process.ProcessState = PCB.State.WAITING;
    }

    public boolean SymbolicLink(String FilePath1, String FilePath2) {
        var node = FoundFile(FilePath2);
        if (node!=null&&node.Type == FileTreeNode.FileType.SYMBOLIC_LINK) {
            var node2=FoundFile(FilePath1);
            if(node2!=null){
                node.contents = FilePath1;
                return true;
            }
        }
        return false;
    }
}
