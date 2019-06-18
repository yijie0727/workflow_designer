package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

public class ContinuousTask implements Callable<Boolean> {

    private static Log logger = LogFactory.getLog(ContinuousTask.class);


    private int blockID;
    private ContinuousBlock currBlock;
    private StringBuilder stdErr = new StringBuilder();
    private Object output;
    private boolean[] errorFlag;

    public ContinuousTask(int blockID, ContinuousBlock currBlock, boolean[] errorFlag) {
        this.blockID = blockID;
        this.currBlock = currBlock;
        this.errorFlag = errorFlag;
    }


    @Override
    public Boolean call() throws IllegalAccessException, IOException, InterruptedException, InvocationTargetException {

        logger.info(" _______________ start Callable call() for id = "+blockID +", name = "+currBlock.getName()+" _______________ ");

        //not execute the block execute method and return the thread if a block in the workflow already caught error
        if(checkError()){
            return false;
        }

        //blocks with inputs
        if(currBlock.getInputs() != null && currBlock.getInputs().size() > 0 ) {
            currBlock.connectIO();

            while (!checkInputsReady()) {
                logger.info(" __ Input not ready for "+currBlock.getId()+" "+currBlock.getName());
                Thread.sleep(2000);
                currBlock.connectIO();
            }
        }

        try{
            output = currBlock.blockExecute();
            currBlock.setFinalOutputObject(output);

        } catch (Exception e){

            e.printStackTrace();
            stdErr.append(ExceptionUtils.getRootCauseMessage(e)+" \n");
            for(String trace:ExceptionUtils.getRootCauseStackTrace(e)){
                stdErr.append(trace+" \n");
            }


            currBlock.setError(true);
            synchronized (errorFlag){ errorFlag[0] = true; }

            logger.error("Error executing id = "+blockID +", name = "+ currBlock.getName()+" Block natively", e);
            throw e;
        }
        currBlock.setStdErr(stdErr.toString());
        currBlock.setCompleted(true);
        return false;
    }



    //if error = true, immediately end the thread
    public boolean checkError(){
        return errorFlag[0];
    }

    public boolean checkInputsReady() throws IllegalAccessException, IOException{
        //check all the inputStreams of this block (already fetch that stream through reflection before the thread task call)
        Field[] inputFields = currBlock.getContext().getClass().getDeclaredFields();
        for (Field f: inputFields) {
            f.setAccessible(true);

            BlockInput blockInput = f.getAnnotation(BlockInput.class);

            if (blockInput != null){
                Object stream = f.get(currBlock.getContext());
                if (stream == null) {
                    return false;
                }
            }
        }

        return true;
    }




    public int getBlockID() {
        return blockID;
    }

    public void setBlockID(int blockID) {
        this.blockID = blockID;
    }

    public ContinuousBlock getCurrBlock() {
        return currBlock;
    }

    public void setCurrBlock(ContinuousBlock currBlock) {
        this.currBlock = currBlock;
    }
}
