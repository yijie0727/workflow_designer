package cz.zcu.kiv.WorkflowDesigner;

import java.util.List;

public class InputField {
    private List<String> sourceParam;
    private List<Integer>sourceBlock;
    private String destinationParam;

    public InputField(List<String> sourceParam, List<Integer> sourceBlock, String destinationParam) {
        this.sourceParam = sourceParam;
        this.sourceBlock = sourceBlock;
        this.destinationParam = destinationParam;
    }

    public InputField() {

    }

    public List<String> getSourceParam() {
        return sourceParam;
    }

    public void setSourceParam(List<String> sourceParam) {
        this.sourceParam = sourceParam;
    }

    public String getDestinationParam() {
        return destinationParam;
    }

    public void setDestinationParam(String destinationParam) {
        this.destinationParam = destinationParam;
    }

    public List<Integer> getSourceBlock() {
        return sourceBlock;
    }

    public void setSourceBlock(List<Integer> sourceBlock) {
        this.sourceBlock = sourceBlock;
    }
}
