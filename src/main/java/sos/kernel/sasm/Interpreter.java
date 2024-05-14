package sos.kernel.sasm;

import sos.kernel.Constants;
import sos.kernel.interrupts.SyscallHandler;
import sos.kernel.mmu.MMUController;
import sos.kernel.models.PCB;

public class Interpreter {
    MMUController mmu;

    final static String[] ComputationCommand = {"add", "sub", "mul", "div"};
    final static String[] MoveCommand = {"mov", "movs"};
    final static String[] JumpCommand = {"jmp", "jif"};
    final static String[] InterruptCommand = {"syscall", "exit"};
    final static String Numbers = "0123456789";

    public Interpreter(MMUController c) {
        mmu = c;
    }

    static boolean contains(String item, String[] arr) {
        for (String s : arr) {
            if (s.equals(item)) return true;
        }
        return false;
    }

    static boolean isMemory(String cmd) { return cmd.charAt(0) == '*'; }

    static int parseOnlyRegister(String cmd) throws Exception {
        var ret = 0;
        if (cmd.equals("SP")) {
            ret = Constants.SP;
        } else if(cmd.charAt(0) == 'R') {
            ret = Integer.parseInt(cmd.substring(1), 10) + 1;
        } else {
            throw new Exception("Computation Executing Failed");
        }
        return ret;
    }

    static int parse2Number(String cmd, PCB process) throws Exception {
        if(Numbers.contains(String.valueOf(cmd.charAt(0))) || cmd.charAt(0) == '-') {
            return Integer.parseInt(cmd);
        } else {
            int r = parseOnlyRegister(cmd);
            return process.RegisterCache[r];
        }
    }

    static int getMemoryAddress(String cmd, PCB process) throws Exception {
        cmd = cmd.substring(1);
        if(Numbers.contains(String.valueOf(cmd.charAt(0)))) {
            return Integer.parseInt(cmd);
        } else {
            return process.RegisterCache[parseOnlyRegister(cmd)];
        }
    }

    static int computation(String cmd, int a, int b) throws Exception {
        return switch (cmd) {
            case "add" -> a + b;
            case "sub" -> a - b;
            case "mul" -> a * b;
            case "div" -> a / b;
            default -> throw new Exception("computation error");
        };
    }

    // return false: interrupted
    public boolean Execute(PCB process, String rawCommand, int CPUTick) throws Exception {
        var commands = rawCommand.split(",");
        for(int i = 0; i < commands.length; i++) {
            commands[i] = commands[i].trim();
        }
        if(contains(commands[0], ComputationCommand)) { // 计算指令
            var left = parseOnlyRegister(commands[1]);
            var right = parse2Number(commands[2], process);
            process.RegisterCache[left] = computation(commands[0], process.RegisterCache[left], right);
        } else if(contains(commands[0], MoveCommand)) { // 移动指令
            // movs
            if(commands[0].equals("movs")) {
                var addr = getMemoryAddress(commands[1], process);
                if(mmu.MemoryWrite(process, addr, commands[2], CPUTick)) { // MMU MemoryWrite/MemoryRead 返回失败时，说明缺页，拉中断
                    return true;
                } else {
                    process.IntPageFault = true;
                    process.IntVirAddr = addr;
                    process.ProcessState = PCB.State.WAITING;
                    return false;
                }
            }
            // mov, mov 不能是内存到内存！
            if(!isMemory(commands[1]) && !isMemory(commands[2])) { // Register <- Register
                var left = parseOnlyRegister(commands[1]);
                var right = parse2Number(commands[2], process);
                process.RegisterCache[left] = right;
            } else if(isMemory(commands[1])) { // Memory <- Register
                var addr = getMemoryAddress(commands[1], process);
                var right = parse2Number(commands[2], process);
                if(mmu.MemoryWrite(process, addr, right, CPUTick)) {
                    return true;
                } else { // MMU MemoryWrite/MemoryRead 返回失败时，说明缺页，拉中断
                    process.IntPageFault = true;
                    process.IntVirAddr = addr;
                    process.ProcessState = PCB.State.WAITING;
                    return false;
                }
            } else if(isMemory(commands[2])) { // Register <- Memory
                var addr = getMemoryAddress(commands[2], process);
                var left = parseOnlyRegister(commands[1]);
                var flag =  mmu.MemoryRead(process, addr, CPUTick);
                if(flag.equals(false)) { // MMU MemoryWrite/MemoryRead 返回失败时，说明缺页，拉中断
                    process.IntVirAddr = addr;
                    process.IntPageFault = true;
                    process.ProcessState = PCB.State.WAITING;
                    return false;
                }
                process.RegisterCache[left] = (int)flag;
            }
        } else if(contains(commands[0], JumpCommand)) {
            if(commands[0].equals("jmp")){
                process.RegisterCache[Constants.SP] = Integer.parseInt(commands[1]) - 1;
                return true;
            }
            var leftNumber = parse2Number(commands[2], process);
            var rightNumber = parse2Number(commands[4], process);
            var flag = switch (commands[3]) {
                case ">" -> leftNumber > rightNumber;
                case "<" -> leftNumber < rightNumber;
                case "==" -> leftNumber == rightNumber;
                case "!=" -> leftNumber != rightNumber;
                case ">=" -> leftNumber >= rightNumber;
                case "<=" -> leftNumber <= rightNumber;
                default -> throw new Exception("Comparing Error");
            };
            if(flag) {
                process.RegisterCache[Constants.SP] = Integer.parseInt(commands[1]) - 1;
            }
            return true;
        } else if(contains(commands[0], InterruptCommand)) {
            if(commands[0].equals("syscall")) {
                return SyscallHandler.Syscall(Integer.parseInt(commands[1]), process, CPUTick);
            } else {
                process.ProcessState = PCB.State.TERMINATED;
                return false;
            }
        } else {
            throw new Exception("Parser Error");
        }
        return true;
    }
}
