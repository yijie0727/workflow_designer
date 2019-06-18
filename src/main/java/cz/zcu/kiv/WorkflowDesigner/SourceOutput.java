package cz.zcu.kiv.WorkflowDesigner;

public class SourceOutput {

    private int sourceBlockID;
    private String sourceParam; //The name of the Output in the source Block

    public SourceOutput(){}

    public SourceOutput(int sourceBlockID, String sourceParam) {
        this.sourceBlockID = sourceBlockID;
        this.sourceParam = sourceParam;
    }

    public int getSourceBlockID() {
        return sourceBlockID;
    }

    public void setSourceBlockID(int sourceBlockID) {
        this.sourceBlockID = sourceBlockID;
    }

    public String getSourceParam() {
        return sourceParam;
    }

    public void setSourceParam(String sourceParam) {
        this.sourceParam = sourceParam;
    }
}
