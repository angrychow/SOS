package sos.kernel.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sos.kernel.models.InterruptVector;
import sos.kernel.models.FileDescriptor;
import sos.kernel.models.FileTreeNode;
import sos.kernel.models.PCB;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static sos.kernel.Main.DeviceTable;

public class FileTreeTest {

    private FileTree fileTree;
    private InterruptVector interruptVector;

    @BeforeEach
    public void setup() {
        interruptVector = new InterruptVector();
        interruptVector.RWQueue = new ArrayList<>();  // 确保 RWQueue 被正确初始化
        fileTree = new FileTree(interruptVector);
        DeviceTable = new ArrayList<>();
    }

    @Test
    public void testCreateFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        boolean result = fileTree.CreateFile("root", node);
        assertTrue(result);
        assertNotNull(fileTree.FoundFile("root/file1"));
    }

    @Test
    public void testDeleteFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);
        boolean result = fileTree.DeleteFile("root/file1");
        assertTrue(result);
        assertNull(fileTree.FoundFile("root/file1"));
    }

    @Test
    public void testOpenFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);

        PCB process = new PCB(1, "TestProcess");
        FileDescriptor fd = fileTree.OpenFile("root/file1", process, true, true, 0);

        assertNotNull(fd);
        assertEquals(process.PCBID, fd.PCBID);
        assertTrue(fd.readable);
        assertTrue(fd.writable);
    }

    @Test
    public void testCloseFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);

        PCB process = new PCB(1, "TestProcess");
        FileDescriptor fd = fileTree.OpenFile("root/file1", process, true, true, 0);

        boolean result = fileTree.CloseFile(fd);
        assertTrue(result);
        assertNull(node.Link);
    }

    @Test
    public void testReadFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);

        PCB process = new PCB(1, "TestProcess");
        FileDescriptor fd = fileTree.OpenFile("root/file1", process, true, false, 0);

        fileTree.ReadFile(fd, 10, 0, process);

        assertEquals(PCB.State.WAITING, process.ProcessState);
        assertFalse(interruptVector.RWQueue.isEmpty());
    }

    @Test
    public void testWriteFile() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);

        PCB process = new PCB(1, "TestProcess");
        FileDescriptor fd = fileTree.OpenFile("root/file1", process, false, true, 0);

        fileTree.WriteFile(fd, 0, process);

        assertEquals(PCB.State.WAITING, process.ProcessState);
        assertFalse(interruptVector.RWQueue.isEmpty());
    }

    @Test
    public void testSymbolicLink() {
        FileTreeNode node = new FileTreeNode();
        node.Name = "file1";
        node.Type = FileTreeNode.FileType.FILE;
        fileTree.CreateFile("root", node);

        FileTreeNode linkNode = new FileTreeNode();
        linkNode.Name = "link1";
        linkNode.Type = FileTreeNode.FileType.SYMBOLIC_LINK;
        fileTree.CreateFile("root", linkNode);

        boolean result = fileTree.SymbolicLink("root/file1", "root/link1");
        assertTrue(result);
        assertEquals("root/file1", linkNode.readContents());
    }
}