package cz.zcu.kiv.WorkflowDesigner;

public class SourceOutput {

    private int sourceBlockID;
    private ContinuousBlock sourceBlock;
    private String sourceParam; //The name of the Output in the source Block

    public SourceOutput(){}

    public SourceOutput(int sourceBlockID, ContinuousBlock sourceBlock, String sourceParam) {
        this.sourceBlockID = sourceBlockID;
        this.sourceBlock = sourceBlock;
        this.sourceParam = sourceParam;
    }

    public int getSourceBlockID() {
        return sourceBlockID;
    }

    public void setSourceBlockID(int sourceBlockID) {
        this.sourceBlockID = sourceBlockID;
    }


    public ContinuousBlock getSourceBlock() {
        return sourceBlock;
    }

    public void setSourceBlock(ContinuousBlock sourceBlock) {
        this.sourceBlock = sourceBlock;
    }

    public String getSourceParam() {
        return sourceParam;
    }

    public void setSourceParam(String sourceParam) {
        this.sourceParam = sourceParam;
    }
}
