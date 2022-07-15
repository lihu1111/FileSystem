import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * 位示图法  -> 总共8K个块 存放在第4号块
 * 此类用于磁盘空闲块管理
 * 为0空闲 为1被占用
 */
public class Block {

    //数组模拟位示图
    static int[] blockInfo = new int[FileSystem.BLOCK_SIZE];

    //申请指定数量的空闲块
    public static ArrayList<Integer> findFree(int num) {
        ArrayList<Integer> list = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < FileSystem.BLOCK_SIZE; i++) {
            if (blockInfo[i] == 0) {
                list.add(i);
                count++;
            }
            if (count == num) {
                break;
            }
        }
        //已经申请到了所需要的空间，更新
        if (list.size() == num) {
            for (Integer free : list) {
                blockInfo[free] = 1;
            }
            writeFree();
        }
        return list;
    }

    //把磁盘中的空闲块读出来
    public static void readFree() {
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            rw.skipBytes(FileSystem.BLOCK_SIZE * 4);
            byte[] bytes = new byte[FileSystem.BLOCK_SIZE];
            rw.read(bytes);
            for (int i = 0; i < FileSystem.BLOCK_SIZE; i++) {
                if (bytes[i] == 0) {
                    blockInfo[i] = 0;
                } else {
                    blockInfo[i] = 1;
                }
            }
            rw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //将内存中的空闲位示图写入磁盘
    public static void writeFree() {
        try {
            RandomAccessFile rw = new RandomAccessFile(FileSystem.DISK_PATH, "rw");
            rw.skipBytes(FileSystem.BLOCK_SIZE * 4);
            byte[] bytes = new byte[FileSystem.BLOCK_SIZE];
            for (int i = 0; i < blockInfo.length; i++) {
                if (blockInfo[i] == 0)
                    bytes[i] = 0;
                else
                    bytes[i] = 1;
            }
            rw.write(bytes);
            rw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
