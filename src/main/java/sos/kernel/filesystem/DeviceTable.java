package sos.kernel.filesystem;

import java.util.Scanner;

public class DeviceTable {
    public static void PrintToSTDOUT(String content) {
        System.out.println(content);
    }

    public static String GetFromSTDIN() {
        var stdin = new Scanner(System.in);
        return stdin.next();
    }
}
