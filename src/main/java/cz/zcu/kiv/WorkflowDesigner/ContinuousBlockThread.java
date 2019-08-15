package cz.zcu.kiv.WorkflowDesigner;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

import static cz.zcu.kiv.WorkflowDesigner.BlockObservation.*;

public class ContinuousBlockThread implements Runnable {
    private static Log logger = LogFactory.getLog(ContinuousBlockThread.class);

    private int id;
    private BlockObservation block;
    private boolean[] errorFlag;    //workflow errorFlag


    private boolean complete;       //bloc
    private boolean error;          //block errorFlag
    private StringBuilder stdErr = new StringBuilder();
    private StringBuilder stdOut = new StringBuilder();

    public ContinuousBlockThread(int id, BlockObservation block, boolean[] errorFlag) {
        this.id = id;
        this.block = block;
        this.errorFlag = errorFlag;
    }

    @Override
    public void run() {

        if (block.getBlockModel() == NORMAL) {
            logger.info("JID: "+block.getJobID()+", block id: "+id+" "+block.getName()+", execute in NORMAL");
            executeModel(NORMAL);        // blockExecute for those NORMAL blocks  (in native or executeAsJAR)
        }
        else if (block.getBlockModel() == MIX){
            logger.info("JID: "+block.getJobID()+", block id: "+id+" "+block.getName()+", execute in MIX");
            executeModel(MIX);
        }
        else if (block.getBlockModel() == PIPE){
            logger.info("JID: "+block.getJobID()+", block id: "+id+" "+block.getName()+", execute in PIPE");
            executeNative();             // execute in native directly for those PIPE blocks
        }

        block.setComplete(complete);
        System.out.println("Complete: blockID: "+id+" "+block.getName());

        try {
            block.updateJSON(error, stdErr.toString(), stdOut.toString());
        }catch (IOException e){
            logger.error("Error update JSON File of id = "+ block.getId()+", name = "+ block.getName()+" Block"+", in jobID "+block.getJobID(), e);
        }
    }

    private void checkPrepared() {
        // check whether the previous blocks are all executed completely
        for( BlockObservation sourceBlock: block.getSourceObservables()) {

            try{
                while((!sourceBlock.isComplete())) {
                    Thread.sleep(100);
                    if(  errorFlag[0] ) return;
                }
            } catch (InterruptedException e){
                logger.error(e);
                error = true;
                errorFlag[0] = true;
            }
        }
    }


    private void executeModel(int blockModel) {
        checkPrepared();
        if(errorFlag[0]) return;

        try{
            block.connectIO();// connect IO and assign blockData

            if(blockModel == NORMAL && block.isJarExecutable() && block.getBlockWorkFlow().getJarDirectory()!=null && !block.isStream() ){
                Object output = block.executeAsJar(stdOut, stdErr);
                block.setFinalOutputObject(output);
                complete = true;
            }
            else{
                executeNative();
            }

        } catch (Exception e){
            logger.error(e);
            error = true;
            complete = true;
            errorFlag[0] = true;
        }
    }


    private void executeNative() {
        try{
            Object output = block.executeInNative();
            block.setFinalOutputObject(output);
            complete = true;

        } catch (Exception e){
            e.printStackTrace();
            logger.error("Execute in native ERROR --> block: id="+block.getId()+", name="+block.getName()+", jobID="+block.getJobID()+ ".  Exception: "+e);
            stdErr.append(ExceptionUtils.getRootCauseMessage(e)+" \n");
            for(String trace:ExceptionUtils.getRootCauseStackTrace(e)){
                stdErr.append(trace+" \n");
            }
            complete = true;
            error    = true;
            errorFlag[0] = true;
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
