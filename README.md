# incf_workflow_designer

## Overview
This project aims at building an easy to use graphical interface that can streamline the configuration of the parameters
controlling individual processing sub-routines and thus make it easy to design complicated data flows and execute them.  
Workflows are designed using individual component blocks that have completely configurable inputs, outputs and 
properties. The Blocks can be combined and rearranged at runtime without making any modification to code. 

Efforts are also targeted to make the tool user friendly and enable easy deployment of workflows on distributed 
computing frameworks.

This project has been developed as a part of Google Summer of Code 2018 by Joey Pinto under the mentorship of the 
International Neuroinformatics Coordinating Facility, Sweden.

More details about the project can be found at http://www.zedacross.com/gsoc2018/

This project hosts the annotations required to make a JAVA class readable into Blocks supported by the designer.
The annotations help reflection identify inputs, outputs, properties and the main execute method needed to convert 
inputs and properties into outputs.

More details about how to use the annotations are available at http://www.zedacross.com/gsoc2018/user-manual

## Contribution Guidelines

### Code Structure

The project is a Maven project and was developed in IntellijIDEA and has a .iml file that can be used to imported. For 
development purposes you may need to run 'mvn install' to make the library available to other projects needing this. Or
you can simply package the project by running 'mvn package' and adding the generated JAR to your classpath.

The cz.zcu.kiv.WorkflowDesigner root package hosts all classes relevant to the workflow definition and execution.

1) The Workflow.java class
 represents an entire workflow. This class can host a workflow with blocks across projects. 
The "initialize" routine executes the main reflection scan on a package and acquires all annotated classes in that 
package. The acquired classes are added to the classloader. The execute method defined in this class is the primary 
entry point for execution of a workflow.

    This file is a good starting place to contribute to the actual workflow execution sequence and logic. Here you have 
access to the entire workflow and can make significant changes to the scheduling of a block's execution. In addition,
the primary reflection based analysis and mapping lies in this file. Any changes to block annotations need to be handled
here as well.

    Mapping output types like graphs, tables, files etc. need to be done here. Also the effect of annotations like runAsJar,
description and so on are controlled in this class as this class also defined the JSON that is exported to blocks.js in
the frontend

2) The Block.java class governs the mapping of Java Objects and the actual execution routine. It maintains the actual
instance of the annotated class as its context. This class does the important function of mapping outputs of the 
previous block to the next corresponding block that needs it. Complex cardinalities and mappings are handled here.
Runtime properties such as file inputs and uploads are also handled here. 

    The Block.java class has a public main method that enables this class to be called as a run-time parameter from a JAR.
This method is used when the class has the runAsJar flag enabled as  true. The inputs and outputs of the jar are loaded
from serialized files.

    Contributions towards new data types and options relevant to a specific block go in this class.

3) The BlockData.java class is the data model for each block used to assist mapping outputs to inputs.


The cz.zcu.kiv.WorkflowDesigner.Annotations package hosts the Runtime annotations used by the project.

The cz.zcu.kiv.WorkflowDesigner.Visualizations package hosts the visualization types for the blocks.

### Dependencies

org.reflections is a significant dependency of this project. Others include dependencies for testing and logging.

### Testing

Tests are defined in the test package. Tests currently include an example handling basic arithmetic operations.

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
 
 

