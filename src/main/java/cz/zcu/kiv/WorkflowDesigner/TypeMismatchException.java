package cz.zcu.kiv.WorkflowDesigner;

public class TypeMismatchException extends Exception {


    public TypeMismatchException(String outputType,String inputType){
        super("The output type of the source block is "+ outputType+", while the input type of the destination block is "+ inputType);
    }
}
