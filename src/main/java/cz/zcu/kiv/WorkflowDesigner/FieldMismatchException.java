package cz.zcu.kiv.WorkflowDesigner;

public class FieldMismatchException extends Exception {
    public FieldMismatchException(String field,String type){
        super("No match found for "+type+" "+ field);
    }
}
