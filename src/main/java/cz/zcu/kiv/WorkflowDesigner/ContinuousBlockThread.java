package cz.zcu.kiv.WorkflowDesigner;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class ContinuousBlockThread implements Runnable {
    private static Log logger = LogFactory.getLog(ContinuousBlockThread.class);

    private int id;
    private BlockObservation block;
    private boolean[] errorFlag;    //workflow errorFlag


    private boolean complete;       //bloc
    private boolean error;          //block errorFlag
    private StringBuilder stdErr = new StringBuilder();

    public ContinuousBlockThread(int id, BlockObservation block, boolean[] errorFlag) {
        this.id = id;
        this.block = block;
        this.errorFlag = errorFlag;
    }

    @Override
    public void run() {

        try{
            Object output = block.executeInNative();


            block.setFinalOutputObject(output);
            complete = true;

        } catch (Exception e){
            logger.error("Execute in native ERROR --> block: id="+block.getId()+", name="+block.getName()+", jobID="+block.getJobID()+ ".  Exception: "+e);
            stdErr.append(ExceptionUtils.getRootCauseMessage(e)+" \n");
            for(String trace:ExceptionUtils.getRootCauseStackTrace(e)){
                stdErr.append(trace+" \n");
            }
            complete = true;
            error    = true;
            synchronized (errorFlag) { errorFlag[0] = true; }
        }



        try {
            block.updateJSON(error, stdErr.toString(), "");
        }catch (IOException e){
            logger.error("Error update JSON File of id = "+ block.getId()+", name = "+ block.getName()+" Block"+", in jobID "+block.getJobID(), e);
        }
    }



    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public BlockObservation getBlock() {
        return block;
    }

    public void setBlock(BlockObservation block) {
        this.block = block;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public boolean[] getErrorFlag() {
        return errorFlag;
    }

    public void setErrorFlag(boolean[] errorFlag) {
        this.errorFlag = errorFlag;
    }


}
