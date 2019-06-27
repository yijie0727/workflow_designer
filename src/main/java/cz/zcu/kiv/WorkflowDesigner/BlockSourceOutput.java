package cz.zcu.kiv.WorkflowDesigner;

public class BlockSourceOutput {

    private int sourceBlockID;
    private BlockObservation blockObservation;
    private String sourceParam; //The name of the Output in the source Block

    public BlockSourceOutput(){}

    public BlockSourceOutput(int sourceBlockID, BlockObservation blockObservation, String sourceParam) {
        this.sourceBlockID = sourceBlockID;
        this.blockObservation = blockObservation;
        this.sourceParam = sourceParam;
    }

    public int getSourceBlockID() {
        return sourceBlockID;
    }

    public void setSourceBlockID(int sourceBlockID) {
        this.sourceBlockID = sourceBlockID;
    }

    public BlockObservation getBlockObservation() {
        return blockObservation;
    }

    public void setBlockObservation(BlockObservation blockObservation) {
        this.blockObservation = blockObservation;
    }

    public String getSourceParam() {
        return sourceParam;
    }

    public void setSourceParam(String sourceParam) {
        this.sourceParam = sourceParam;
    }
}
