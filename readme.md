## 北京邮电大学 操作系统课程设计 SOS

SOS 是一个汇编脚本解释器 + 任务调度器。

SOS 有五个模块

- 汇编脚本逐行解释器（模拟 CPU）
- Scheduler
- Memory Manager
- File System & I/O
- 前端

### 汇编脚本解释器：Software CPU

#### SOS 中：汇编脚本解释器等价于物理 CPU

```JavaScript
function CPU_Tick(
    instructionAddress
) {
    const instruction = Memory[instructionAddress] // 取指
    ScriptExecute(Registers, Memory, Instruction) // 执行
    if(InterruptBits[0]) { // 中断周期
        TimerInterruptService()
    } else if(InterruptBits[1]) {
        IOInterruptService()
    } else if(InterruptBits[2]) {
        SemaphoreReleaseService()
    }
    CPU_Tick_Numbers += 1
}
```

汇编脚本解释器等价于 CPU 的指令执行，飞轮图如下：

暂时无法在飞书文档外展示此内容

#### 寄存器与指令集

我们的汇编脚本解释器实质上行使相当于 CPU 的能力。汇编脚本解释器只需要两个特别的寄存器：

CR：页表寄存器

PC：程序计数器

剩下全是通用寄存器，R1 ~ R30，合计 32 个寄存器。

每一条汇编命令名义上都是 64 位的，在内存中的每一条 64 位的 item 只是一条指针，指向的是 Software CPU 所依赖的真实物理机上存放单条汇编命令（字符串格式），这么做是为了方便解析，降低工作量。

在解释执行汇编脚本的时候，我们使用 Tick 模拟时钟控制 CPU 的执行速率。CPU.Tick() 执行一次解释。

值得注意的是，汇编脚本解释器在访问内存的时候需要访问用户无法直接访问的寄存器 CR 来查询页表，进行虚实转换。详情查看任务调度器模块。

汇编指令有：

- 算术运算命令：
  - `add, R1, R2` : R1 = R1 + R2
  - `sub, R1, R2` : R1 = R1 - R2
  - `mul, R1, R2` : R1 = R1 * R2
  - `div, R1, R2` : R1 = R1 / R2
- 数据传送命令：
  - `mov, R1, *R2`: R1 = *R2（[Reg]是间址操作）
  - `mov, R1, 100`: R1 = 100 （$指示立即数）
  - `movs, R1, "string"`: 简写，从 R1 指示的地址处开始，将 `"string"` 写入内存。
- 跳转命令：
  - `jmp R1`：pc = R1 - 1
  - `jif R1, R2 > R3`: 如果 R2 > R3 : pc =  R1 - 1
    - 汇编脚本解释器直接支持不等于比较，所以不设类似于 CF OF 等标志位寄存器
    - 支持：`>` `>=` `<` `<=` `!=`
- 特殊命令：
  - `syscall [syscall number]`
    - 陷入系统调用
    - 具体信息和调用规则，查看系统调用表
  - `exit`
    - 程序结束 

#### 汇编脚本规定

| 0B - 2047B | Code Segment |
| ---------- | ------------ |
| 2047B - ∞B | Heap Segment |

代码空间至多 2048 Byte。如果要访问高于 2047 的虚拟地址，则需要通过 Malloc 系统调用分配内存。

### 内存管理器：Memory Manager

#### 接口与功能

内存管理器申请页面的功能非常简单：

- 脚本解释器访问内存，访问内存时**指明进程号和虚拟地址**
  - `AccessMemory(int VirtualAddress, int ProcessNumber)`
  - `WriteMemory(int VirtualAddress, int ProcessNumber, byte Content)`

上文我们提到，实际代码段只有 2048B。我们假定页面为 1KB (2^10B)，那么一个程序一开始最多 2 页。

#### MM 的实现

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=NDZkYzNjZDZhMDBmYmFkM2RiOWQwZThkMjZhMzYzNmJfZ29OaUxxbEtzcXdidnoxM1VmcGwzeWxmMzgwVWNKdk5fVG9rZW46RUE4eWJJQm1Cb2ZjT014M0NVWGN5YWZpblNmXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)

MemoryManage 模块，实际管理 SOS 宿主机上的模拟内存（下称 Memory[2^16]，为体现换页可以弄得更小，具体数值待定。）的分配与释放。

- MM 将 Memory[2^16] 切成一个一个页，然后分配给不同进程。

#### 内存访问

我们规定每一个进程能够拥有的页框的总数是有限的。我们定义一个进程在运行时页表有效页表项数为一个进程能够拥有的页框数，我们规定这个数字是 MaxPages。

考虑如下公式：

1. 虚拟地址 = 页号 × 页大小 + 页内偏移量
2. 实际地址 = 物理页号 × 页大小 + 页内偏移量

虚拟空间一共 16 位，其中页内地址一共 10 bits（2^10B），剩下 6 位是页表地址

所以一个页表应该有 2^6 项

[010001]（虚拟页号）[0111010101]（页内地址）

当任务访问内存时，他调用读入、读出内存。如果虚拟地址页表命中失败，**那么触发一次缺页中断**，当前进程阻塞，缺页中断服务结束后中断位被置1。缺页中断服务期间，MM 将 Memory[]中的一段分配给指定进程，并将物理地址的虚实转换进行填表。页表的地址通过 PCB 的 CR 寄存器存储。

页表的数据结构如下：

```C
struct PageTableEntry {
    int PhysicalPageNumber;
    bool isWrite;
    bool isRead;
    bool valid;
    int LastVisitCPUTick;
};

struct PageTableEntry PageTable[1<<6];
```

考虑页表的换入与换出；如果产生页面被换出的情况，我们需要一个 SwappedOutPages 的数据结构存储被换出的页面，这个数据结构会在缺页中断服务阶段中被使用到。SwappedOutPages 像是一个字典，给定进程号和虚拟地址的页号，返回被换出的页面。

具体的换出页面流程是：

- 页面缺页（页表项失效），准备换页。
- 进入缺页处理程序，检查换入的页是否在 SwappedOutPages 中：
  - 如果在，准备将此页调入内存。
  - 如果不在， 准备将一个【页内每一地址的内容均为 0 的页面】调入内存。
- 如果此时一个页面的拥有的页面数大于 MaxPages，我们就要利用页面调度算法（LRU）选择一个页面进行换出。换出的页面需要缓存进入 SwappedOutPages 中。
- 将准备的页面调入内存中。

如果一个进程被中断，并且我们认为他会在将来的一段时间内不会被再次被调度到，那我们可以触发 MidTime Schedule：

- MidTime Schedule 会主动的将进程所占用的内存页给调出内存。
- 被 MidTime Schedule 调度的进程的 PCB 需要打上 Swapped 的 Tag。
- 当被打上 Swapped Tag 的进程再一次被选中执行的时候，我们可以提前将其 Swapped Out 的内存换入内存中，防止过量缺页中断的产生。

### 任务调度器：Process Scheduler

Software CPU 对用户仅仅展示 PC、R1-R30 的寄存器。

然而任务调度器需要保存更多的信息，例如当前任务的页表（页表首地址存入 CR）。

#### PCB 结构

首先定义数据结构 PCB：

- 任务寄存器快照：PC（初始为 0）、R1-R30、CR。
- 任务优先级 Priority。
- 任务当前状态：`Running | Ready | Waiting`。
- 任务堆（Heap）及其对应页表的管理，堆段从 4096 开始增长：
  - 堆是运行时可分配的内存空间，我们用内存分配链表来维护这一数据结构
  - 具体怎么维护见中断向量表之 malloc mfree。

#### 任务拉入内存

SOS 为开发便捷使用 Process Scheduler 帮助任务拉入 SOS 的内存（获取页框等功能均通过 Memory Manager 模块），这里分两种：

- 手动提交任务：通过前端直接将代码拉入内存。
- Exec 系统调用：通过系统调用将 SOS 文件系统内的代码拉入内存。

拉入内存的过程包含将任务拉入内存、建立 PCB 结构、建立页表及页表映射（后两者依靠 MM 实现）。

- 拉入内存：Process Scheduler 通过 MM 向内存写入代码。

- 建立 PCB 结构：初始化 PCB、设置任务优先级、当前状态。

- 初始化页表：将获取的页框的地址同虚拟地址结合起来。我们假定每一个 Process 的 Code 段地址从 0 号虚拟地址出发，我们规定每一条汇编指令的长度为 1Byte，向下增长。

  - 

  - | 0B - 2047B | Code Segment |
    | ---------- | ------------ |
    | 2048B - ∞  | Heap Segment |

  - 暂时无法在飞书文档外展示此内容

  - 任务在运行过程中还会申请内存，我们需要维护任务的堆的数据结构。代码空间至多 2048 Byte。如果要访问高于 2047 的虚拟地址，则需要通过 Malloc 系统调用分配内存。

#### 任务调度

任务调度器的功能是在时间片到/当前任务陷入中断的时候切换任务。

这里我们用同优先等级时采用时间片轮转、高优先级队列空时下一等级优先级可以调度的算法进行调度。**为实现任务调度，我们需要有一个时间片到的时钟中断源**。

调度流程：

- 保存当前 Process 的执行上下文（CPU的寄存器内容保存到 PCB）。
- 改变当前 Process 状态。
- 选择下一个 Process（调度算法）。
- 恢复选择的 Process 的执行上下文（将 PCB 中的寄存器内容恢复）。

### 系统调用表与中断向量表

#### 中断的实现方式

在真实的操作系统中，我们将中断向量表的入口地址送入，中断发生后通过中断向量表获得处理程序。但是在 SOS 中，我们并不死板的按照这种设计进行中断。在每一个 Tick 周期结束后，我们检查对应的中断位，例如：

- 时钟周期是否到期？
- IO 任务是否处理完成？

等。伪代码是：

```JavaScript
function CPU_Tick(
    instructionAddress
) {
    const instruction = Memory[instructionAddress] // 取指
    ScriptExecute(Registers, Memory, Instruction) // 执行
    if(InterruptBits[0]) { // 中断周期
        TimerInterruptService()
    } else if(InterruptBits[1]) {
        IOInterruptService()
    } else if(InterruptBits[2]) {
        SemaphoreReleaseService()
    }
    CPU_Tick_Numbers += 1
}
```

可能的系统调用表：

| 调用名称             | 调用号 | R1（返回值）                                    | R2                           | R3                         | R4             |
| -------------------- | ------ | ----------------------------------------------- | ---------------------------- | -------------------------- | -------------- |
| malloc               | 1      | 申请内存首地址，失败返回负数                    | 申请内存大小                 |                            |                |
| mfree                | 2      | 失败返回负数                                    | 内存首地址                   |                            |                |
| open                 | 3      | File descriptor，失败返回负数                   | 文件路径，字符串指针地址     | Open 模式位                |                |
| close                | 4      | 失败返回负数                                    | File descriptor              |                            |                |
| read                 | 5      | Read 内容，失败返回负数                         | File descriptor              | Read 模式位                | Buffer Size    |
| write                | 6      | 失败返回负数                                    | File descriptor              | Write 模式位               | Buffer Pointer |
| delete               | 7      | 失败返回负数                                    | 文件路径，字符串指针地址     | Exec 模式位                |                |
| mkdir                | 8      | 失败返回负数                                    | 父文件夹路径，字符串指针地址 | 文件夹名称，字符串指针地址 |                |
| create               | 9      | 失败返回负数                                    | 父文件夹路径，字符串指针地址 | 文件名称，字符串指针地址   |                |
| timer                | 10     | 失败返回负数                                    | Timer 时钟                   |                            |                |
| wait（等待信号量）   | 11     | 失败返回负数（注意因为semaphore被阻塞不是失败） | Semaphore 号                 |                            |                |
| signal（释放信号量） | 12     | 失败返回负数                                    | Semaphore 号                 |                            |                |

可能的中断位与中断处理程序：

| 中断号 | 名称         | 作用                                       |
| ------ | ------------ | ------------------------------------------ |
| 0      | 时间片到     | 时钟（包括但不限于轮转时间片、软件时间片） |
| 1      | IO 结束      | 文件 IO 结束                               |
| 2      | 中断服务结束 | 中断服务结束，内存可以重新访问             |

#### 第一组：malloc 与 mfree

每一个进程都维护一个堆表。堆表是动态申请的内存空闲表。

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=YjY4MzNlN2E3MjAwMzJjMTQ1NjFkZmFmMDhjZWYxZWRfMzdYN2x1RjcxY1VHRE05RHdBNHpJOFpLUlMzZ2VRTFpfVG9rZW46UkxYVGJlRWZVb3Vwa1N4V1M1Q2NaN1pnbnplXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)

比如某进程有 1 个内存页面用于放置堆变量，进程没有申请内存，那么堆表就是：[0 ~ 63B]；如果进程申请 7B 的堆表，那么堆表就会变成：[7B ~ 63B]。经过一段时间后，堆表可能变成：[31B ~ 42B] -> [61B ~63B]，这意味着只有两块内存是闲置的。

malloc 系统调用发生时，操作系统应该查看：是否堆表有足够的空闲内存分配给此次请求。如果够则分配，不够的则拒绝分配。

- 例如堆表：[31B ~ 42B] -> [61B ~63B]，申请 12B 内存
- 堆表目前内存不够，拒绝分配。
- 分配内存。注意，堆表上的地址应当是堆段上的虚拟地址。堆段实际应当从 2048 开始增长。

mfree 与之相反。

#### 第二组：open close read write mkdir create

SOS 利用树形数据结构来组织文件系统，以提供有效的文件管理和存储功能。

当应用程序在 SOS 中打开文件时，SOS 的文件系统调用 open 将返回一个文件描述符（file descriptor），这个文件描述符是一个唯一的标识符，用于标识进程和文件系统中的文件之间的关联。同时，SOS 维护着一张文件描述符表（fd 表），它记录了每个进程所打开的文件和相应的文件描述符，以便进程可以通过文件描述符来访问和操作文件。

当应用程序完成对文件的操作后，可以调用 close 函数来关闭文件，这将使得该文件描述符在fd表中的表项作废，释放相关的资源并确保文件的完整性和安全性。

为了进行文件的读取和写入操作，应用程序可以使用 read 和 write 函数，通过指定文件描述符和相应的数据进行读取和写入操作。这样，SOS 能够将进程的文件读写操作与文件系统中的具体文件关联起来，确保数据的正确传输和持久化存储。

SOS 支持 mkdir 操作，可以通过调用该函数来创建文件夹。创建文件夹时，SOS会在文件系统中相应的位置创建一个新的目录，用于存放其他文件和子文件夹，从而帮助用户组织和管理文件结构。除了创建文件夹，SOS 还提供 create 函数用于创建文件。

#### 第三组 timer

Timer 是时钟源中断。使用该系统调用令程序陷入阻塞状态，时钟到后将程序唤醒。

#### 第四组 wait signal

SOS 内部维护一定数量的“信号量”，每一个信号量对应着一片共享内存。每一个程序可以占有一个信号量和释放一个信号量。

当一个程序尝试占有一个信号量时：

- 操作系统检查是否有其他程序正在占用这个信号量。
  - 如果是，那么当前程序陷入阻塞状态
    - 操作系统内部维护一个信号量阻塞队列，记录哪些进程正在等待某个信号量的释放。
  - 如果没有任何程序陷入占有这个信号量，那么 SOS 标记该信号量为当前进程所有，程序继续正常进行

当一个程序释放信号量时

- 操作系统将信号量标记为无人使用
- 操作系统将信号量对应的阻塞队列的所有程序从阻塞态（Waited）转换为就绪态（Ready）

### 文件系统

#### 文件的组织

SOS 的 FS 应当如同 Linux，利用树形结构进行组织。文件系统设计 FD 表，将文件与进程读写产生一个关联。所有进程共享同一份 FD 表。这里与 Linux 不同，Linux 有一个局部的 FD 表，还有一个全局的表。

| FD 号 | 文件路径          | 读写权限 | 光标位置 |
| ----- | ----------------- | -------- | -------- |
| 1     | /home/david/file1 | read     | 10       |

注意设置读写**互斥**规则。我们应当在这里实现进程互斥。

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=Yzg5MjkxMzJhYmU5ZTY5NTc5ZThmYTIyMmUwODFkZjVfd2dMS3kzQ29BV2lKS1V4YlRrZmRzcUlESzE4WWFESjJfVG9rZW46TXR0YWJoMUNsb2VRbGF4N1c3SWM4T3ZBbmFlXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)

Linux 这么设计文件系统，我们把 FD 和 File Table 二合一。FD 就是对所有用户程序可见的。由于我们的文件系统不会涉及到物理设备的落盘问题，我们考虑直接把 inode 作为我们抽象的最底层。我们的文件系统存 Inode 的路径地址即可。

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=MmVhMGM2M2ZmYTcxYjViNzAyYjI3Yjk0NjEwMWNjY2NfMlUxWEFIN2dHdTlmWFpUblRWcE5YdkJEejBOdlRiVGFfVG9rZW46UmJHNGJzbVZmb3lQNjN4UlRheWM4WURJblZkXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)

Linux 采用文件树结构，总体来说文件分为 Directory 和 File 两类。SOS 用如下的数据结构定义文件及其嵌套关系：

```C
struct dir_entry {
    char filename[MAX_FILENAME_LEN];
    uint8_t idx;
};

struct file_blk {
    uint8_t busy; // 指示该块是否被忙
    mode_t mode;
    uint8_t idx;
    union {
        size_t file_size;
        size_t dir_children;
    };
    char data[NAIVE_FS_BLOCK_SIZE];
    uint8_t next_block_idx;
};
```

我们的操作系统只负责存储 inode 节点。inode 节点到文件字节流的传输请看 IO 系统。

#### 文件读写互斥

注意到不同的程序对同一个文件的读和写应该是互斥的，所以我们应当在 read write 系统调用的实现上实现读写锁。

1. 读锁（共享锁）：多个线程可以同时获取读锁并同时读取共享资源，读操作之间不会相互干扰。读锁是共享的，即多个线程可以同时持有读锁，但无法同时持有写锁。
2. 写锁（独占锁）：写锁是互斥的，一旦一个线程获取了写锁，其他线程无法获取读锁或写锁，直到写锁被释放。写锁用于保护对共享资源的写入操作，确保在写操作期间没有其他线程能够读或写该资源。

同样的，我们需要在操作系统内部为每一个文件准备一个文件读写阻塞队列。

### IO 系统

IO 系统的功能是将 SOS 的 inode 与 SOS 的宿主的文件系统连接起来

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=ZDM2YTJlN2ZiMWJkZjc0YTIwYjY4MWMzOTEyODhkYjdfdk9MTmxRQ0dxVW5SdW11OEFZdU9rN25tVmp6SVdGZUJfVG9rZW46U1JNc2JvSUh6bzNaR3h4cGxRSGNjeXFKbmpiXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)

Linux 的 inode 结构与具体的设备相连接。 SOS 的文件系统不需要这么复杂。SOS 的 inode 记录格式是：

- 文件的名称
- 文件的路径，作为唯一的标识符
- 文件的类型（有可能是输出屏幕，有可能是键盘，有可能是磁盘文件）
- 设备号（设备号对于我们的 SOS 而言只是一个字符串，IO 系统负责将设备号同宿主设备连接起来）

暂时无法在飞书文档外展示此内容

IO 完成后将中断位被置 1，脚本解释器在下一 Tick 执行 IO 完成中断服务程序。

### 前端

前端的功能是在线展示并修改 SOS 的运行状况。

#### 可视化展现当前时刻寄存器、内存快照（Snapshot）

前端可以可视化展现：

- PCB 序列。当前驻留在内存的所有进程的运行状况、PCB Cache。
- SOS CPU 寄存器数值
- CPU Tick
- 每一个进程的页表项（页表项过多，仅展示活跃的表项）
- 内存页表使用情况

#### 单步运行 SOS，或者按某频率执行 SOS

考虑到脚本执行器执行一条指令可能需要多个 Tick (用户态陷入操作系统态)，**单步运行不等于只让整个系统只运行一个 Tick。**

#### 创建任务，交给 SOS 执行

前端可以提交 SOS Assembly 到 SOS Kernel，手动创建任务。

#### 可视化文件系统

前端可以可视化 SOS 的 FileSystem，使用组件 File Explorer 将 File Tree 渲染出来。同时，用户可以在前端创建、查看、修改、删除文件。

![img](https://ogzcimfcsg.feishu.cn/space/api/box/stream/download/asynccode/?code=NDUxMjg4ZGUzMzMzOGU3NjI2NjliZDg5NTQ4MjI3ZmJfTnNGR1lIT3JkeEpMM3YzZDVTNWgxdjk3WjRhajJ2bldfVG9rZW46R2JwVGI4Wnc0b0hhM2R4NG16QWNNb0tSbjBmXzE3MTY4NzM5Mjc6MTcxNjg3NzUyN19WNA)