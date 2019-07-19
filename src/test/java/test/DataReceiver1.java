package test;

import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockExecute;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockInput;
import cz.zcu.kiv.WorkflowDesigner.Annotations.BlockType;

import java.io.ObjectInputStream;
import java.io.PipedInputStream;

import static cz.zcu.kiv.WorkflowDesigner.Type.STREAM;

@BlockType(type ="DataReceiver1", family = "PIPE")
public class DataReceiver1 {

    @BlockInput(name = "PipeIn1", type = STREAM)
    PipedInputStream pipedIn1   = new PipedInputStream();

    @BlockInput(name = "PipeIn2", type = STREAM)
    PipedInputStream pipedIn2   = new PipedInputStream();

    @BlockExecute
    public void process() {

        try{
            ObjectInputStream objectInputStream1 = new ObjectInputStream(pipedIn1);
            ObjectInputStream objectInputStream2 = new ObjectInputStream(pipedIn2);



            for(int i = 0; i<3; i++){
                Cat cat = (Cat) objectInputStream1.readObject();

                System.out.println("Receiver1: "+cat.getName()+ ", "+cat.getColor()+", "+cat.getSize());
            }

            for(int i = 0; i<3; i++){
                Cat cat = (Cat) objectInputStream2.readObject();

                System.out.println("Receiver1: "+cat.getName()+ ", "+cat.getColor()+", "+cat.getSize());
            }


            pipedIn1.close();
            pipedIn2.close();
            objectInputStream1.close();
            objectInputStream2.close();

        } catch(Exception e){
            e.printStackTrace();
        }


    }
}
