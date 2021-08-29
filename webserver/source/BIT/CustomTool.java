package BIT;

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.*;

public class CustomTool {
    private static int dyn_instr_count = 0;

    //private static int newcount = 0;

    // private static int loadcount = 0;
    // private static int storecount = 0;

    private static HashMap<Long, Long> loadcount = new HashMap<>();
    private static HashMap<Long, Long> storecount = new HashMap<>();


    public static void doIntructionsCount(File in_dir, File out_dir)
    {
        String filelist[] = in_dir.list();

        for (int i = 0; i < filelist.length; i++) {
            String filename = filelist[i];
            if (filename.endsWith(".class")) {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);
                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();
                    //routine.addBefore("StatisticsTool", "dynMethodCount", new Integer(1));

                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("BIT/CustomTool", "dynInstrCount", new Integer(bb.size()));
                    }
                }
                //ci.addAfter("StatisticsTool", "printDynamic", "null");
                ci.write(out_filename);
            }
        }
    }

    public static synchronized void dynInstrCount(int incr)
    {
        dyn_instr_count += incr;
        //dyn_bb_count++;
    }

    public static void doLoadStore(File in_dir, File out_dir)
    {
        String filelist[] = in_dir.list();

        for (int i = 0; i < filelist.length; i++) {
            String filename = filelist[i];
            if (filename.endsWith(".class")) {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);

                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();

                    for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); ) {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode=instr.getOpcode();
                        if (opcode == InstructionTable.getfield)
                            continue;
                            //instr.addBefore("StatisticsTool", "LSFieldCount", new Integer(0));
                        else if (opcode == InstructionTable.putfield)
                            continue;
                            //instr.addBefore("StatisticsTool", "LSFieldCount", new Integer(1));
                        else {
                            short instr_type = InstructionTable.InstructionTypeTable[opcode];
                            if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
                                instr.addBefore("BIT/CustomTool", "LSCount", new Integer(0));
                            }
                            else if (instr_type == InstructionTable.STORE_INSTRUCTION) {
                                instr.addBefore("BIT/CustomTool", "LSCount", new Integer(1));
                            }
                        }
                    }
                }
                ci.addAfter("BIT/CustomTool", "printMetrics", "null");
                ci.write(out_filename);
            }
        }
    }

    public static synchronized void LSCount(int type)
    {
        long id = Thread.currentThread().getId();
        if (type == 0)
            if(loadcount.containsKey(id)) {
                loadcount.put(id, loadcount.get(id) + 1);
            } else {
                loadcount.put(id, new Long(1));
            }
        else {
            if(storecount.containsKey(id)) {
                storecount.put(id, storecount.get(id) + 1);
            } else {
                storecount.put(id, new Long(1));
            }
        }
    }

    public static void printMetrics(){
        System.out.println("Dynamic instruction count: " + dyn_instr_count);
        System.out.println("Load count: " + loadcount);
        System.out.println("Store count: " + storecount);
    }

    public static void main(String argv[]){
        try {
            File in_dir = new File(argv[0]);
            File out_dir = new File(argv[1]);

            if (in_dir.isDirectory() && out_dir.isDirectory()) {
                doIntructionsCount(in_dir, out_dir);
                doLoadStore(in_dir, out_dir);
            }
            else {
                System.out.println("Invalid Arguments");
            }
        }
        catch (NullPointerException e) {
            System.out.println("Invalid Arguments");
        }
        //do metrics and store them
    }

    public static int getTotalInstructionsCount(){ return dyn_instr_count;}

    public static Long getLoadcount(Long id){
        if(loadcount.containsKey(id)) {
            return loadcount.get(id);
        }
        return null;
    }

    public static Long getStorecount(Long id){ 
        if(storecount.containsKey(id)) {
            return storecount.get(id);
        }
        return null;
    }

    public static void clearMetrics(Long id) {
        loadcount.remove(id);
        storecount.remove(id);
    }
}
