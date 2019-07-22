package cz.zcu.kiv.WorkflowDesigner;

public class WrongTypeException extends Exception  {

    public WrongTypeException( ){
        super("@BlockType continuousFlag() of blocks in one workFlow can only either be True or False");
    }

}
