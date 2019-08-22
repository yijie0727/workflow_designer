 # INCF Workflow Designer
  
 ![Google Summer of Code img](https://4.bp.blogspot.com/-AY7eIsmbH0Y/WLRdpe78DJI/AAAAAAAABDU/lsb2XqcmyUsLqYo6yzo9HYMY4vLn3q_OgCLcB/s1600/vertical%2BGSoC%2Blogo.jpg)

 ## Overview
 - This project aims at building an easy to use graphical interface that can streamline the configuration of the parameters
 controlling individual processing sub-routines and thus make it easy to design complicated data flows and execute them.  
 Workflows are designed using individual component blocks that have completely configurable inputs, outputs and 
 properties. The Blocks can be combined and rearranged at runtime without making any modification to code. 
 
 - Efforts are also targeted to make the tool user friendly and enable easy deployment of workflows on distributed 
 computing frameworks.
 
 - This project has been developed as a part of Google Summer of Code 2018 by [Joey Pinto](https://github.com/pintojoey) 
 and Google Summer of Code 2019 by [Yijie Huang](https://github.com/yijie0727) under the mentorship of the International 
 Neuroinformatics Coordinating Facility, Sweden.
 
 - In GSoC2018 phase, this maven project was mainly focused on the development of web-based GUI and configurable workflow 
 blocks to deal with the cumulative data, all the blocks in one workflow were executed one by one by checking the dependency 
 among the blocks repeatedly. Worked based on the [GSoC2018 summary](http://www.zedacross.com/gsoc2018/), the 
 [GSoC2019](https://gist.github.com/yijie0727/b2b9d2964d2b81fd682398db330c161f) aims at modifying the existing workflow 
 designer to enable also running of continuous workflows(the continuous stream processed in the blocks in a workflow 
 without waiting the previous blocks finishing execution). 
 
 
 ##Contribution Guidelines
 
 ### Code Structure
 
 The project is a Maven project and was developed in IntellijIDEA and has a .iml file that can be used to imported. For 
 development purposes you may need to run 'mvn install' to make the library available to other projects needing this. Or
 you can simply package the project by running 'mvn package' and adding the generated JAR to your classpath.
 
 The cz.zcu.kiv.WorkflowDesigner root package hosts all classes relevant to the workflow definition and execution:
 
 ##### 1) The BlockWorkflow class
 - It represents an entire workflow. This class can host a workflow with blocks across projects. 
 The "initializeBlocks" routine executes the main reflection scan on a package and acquires all annotated classes in that 
 package. The acquired classes are added to the classloader. The execute method defined in this class is the primary 
 entry point for execution of a workflow.
 
 - This file is a good starting place to contribute to the actual workflow execution sequence and logic. 
 When you press Schedule in [workflow GUI](https://github.com/NEUROINFORMATICS-GROUP-FAV-KIV-ZCU/workflow_designer_server),
 your workflow json will be sent to the method "execute" in this class. It will classify the workflow type(cumulative, 
 continuous, or mixed), and then process then differently.(Also for some particular workflows which specifically deals with 
 Lab Streaming Layer data can only be executed locally, users can configure their own workflows on GUI and export the workflow
 in json, assign this value to the method directly to execute locally.)
 Here you have access to the entire workflow. In addition, the observer and observable analysis for Observer Pattern, 
 and mapping blocks and indexes lie in this file. Any changes to block annotations need to be handled here as well.
 
 - Mapping output types like graphs, tables, files etc. need to be done here. Also the effect of annotations like runAsJar,
 description and so on are controlled in this class as this class also defined the JSON that is exported to blocks.js in
 the frontend.
 
 
 
 ##### 2) The BlockObservation class 
 - This class governs the mapping of Java Objects and the actual execution routine. It maintains the actual
 instance of the annotated class as its context. This class does the important function of mapping outputs of the 
 previous block to the next corresponding block that needs it. Complex cardinalities and mappings are handled here.
 Runtime properties such as file inputs and uploads are also handled here. 
 
 - It extends Observable class and implements Observer, Runnable interfaces to realize the Observer Pattern and multiple
 threads. In one workflow, source blocks will be treat as Observables and their destination blocks are corresponding 
 Observers(this relationship is stored in two maps in BlockWorkflow class according to the "edge" json array). 
 Only in cumulative workflow, when blocks execute completely, they will update and inform their Observers as Observables,
 so once the next block receive all its source blocks' update, it will automatically execute without any other dependency
  checking. Multiple blocks can run aa the same time in different threads if they don't have dependency.
  
 - The Block.java class has a public main method that enables this class to be called as a run-time parameter from a JAR.
 This method is used when the class has the runAsJar flag enabled as true. The inputs and outputs of the jar are loaded
 from serialized files or through RMI depending on the jarRMI flag is false or true.
 
 - Contributions towards new data types and options relevant to a specific block go in this class.
 
 
 
 ##### 3) The PipeTransitThread  class
 - This class is designed when dealing with continuous or mixed workflow. It is used for each block's output, to send 
 continuous data to all its next destination blocks. Since every block is executed in its own thread, pipe communication
  is used to send continuous stream between blocks. It uses the relationship existing among the outputs of source blocks
  and the inputs of next blocks assigned in the BlockObservation class to transfer continuous data.
  
 - Once the previous send data, the next one will receive data until no continuous data exists and close the stream.
 
 - For mixed workflow, if the next destination is mixed block, all the continuous stream data will be stored in temp 
 file first, until all its dependency blocks are complete, then read stream from the temp file.
 
 
 
 ##### 4) The ContinuousBlockThread class
 - This class implements Runnable interface and is used for mixed or continuous blocks execute in parallel. Since 
 mixed or continuous blocks will deal with continuous data, it cannot simply use the Observer Pattern and execute based 
 on the block complete flag, otherwise it will become the cumulative blocks. When dealing with the continuous blocks,
 they want to receive data continuously when the source blocks generate data continuously, thus executing blocks 
 externally as a jar is no longer applicable. 
 
 
 
 ##### 5) The BlockSourceOutput class 
 - This file is used for the value of IOMap of each block to collect all the source blocks of one destination block



 ##### 6) The BlockData class 
 - This class is the data model for each block used to assist mapping outputs to inputs.
 
 
 
 ##### 7) Annotations package
 - The cz.zcu.kiv.WorkflowDesigner.Annotations package hosts the Runtime annotations used by the project.
 - This project hosts the annotations required to make a JAVA class readable into Blocks supported by the designer.
 The annotations help reflection identify inputs, outputs, properties and the main execute method needed to convert 
 inputs and properties into outputs. More details about how to use the annotations are available at 
 [GSoC2018 user manual](http://www.zedacross.com/gsoc2018/user-manual).
 
 - Since this part is modified in GSoC 2019, @BlockType annotation is improved:
 One more field is added: jarRMI, which is used as a flag to denote the data transfer mode when block's @BlockType 
 runAsJar is set to true. If both runAsJar and jarRMI are set to true, then this block will be execute as jar externally, 
 receive its inputs and send its outputs through RMI, instead of through serializing data to file, deserializing file 
 to data when jarRMI is false.
 
  - Also when designing the continuous block, to deal with the continuous stream, its related input and output should be 
  denoted as @BlockInput and @BlockOutput with type = STREAM.
 ```Java
@BlockType(type ="ArithmeticBlock", family = "Math", runAsJar = true, jarRMI = true)
public class Arithmetic {}
 
@BlockType(type ="StreamProviderBlock", family = "Stream", runAsJar = false)
public class StreamProvider {
    @BlockProperty(name = "Value", type = STRING )
    String value;
    
    @BlockOutput(name = "OutputStream", type = STREAM)
    PipedOutputStream pipedOut = new PipedOutputStream();
    
    @BlockExecute
    public void process() throws Exception {}
 }
 ```

 
 ##### 8) Visualizations package
 The cz.zcu.kiv.WorkflowDesigner.Visualizations package hosts the visualization types for the blocks.
 
 ### Dependencies
 
 org.reflections is a significant dependency of this project. Others include dependencies for testing and logging.
 
 ### Testing
 
 Tests are defined in the test package. Tests currently include an example handling basic arithmetic operations, dealing 
 with continuous stream data.
 
 ### Possible Issues
 
 Memory-intensive blocks can cause java heap space error.
 
 Solution: Set the system property workflow.designer.vm.args=-Xmx4G
 
 ### Copyright
 
  
   This file is part of the Workflow Designer project
 
   ==========================================
  
   Copyright (C) 2018 by University of West Bohemia (http://www.zcu.cz/en/)
  
  ***********************************************************************************************************************
  
   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
   the License. You may obtain a copy of the License at
  
       http://www.apache.org/licenses/LICENSE-2.0
  
   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
   an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
   specific language governing permissions and limitations under the License.
  