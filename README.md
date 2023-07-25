# FormVu Microservice Example #

IDRsolutions' FormVu Microservice Example is an open source project that allows you to convert PDF Forms to HTML5 by running FormVu as a web service in the cloud or on-premise. 

**Please note that FormVu is paid-for, commercial software - however you can use the trial version for free. Just rename the trial .jar to "formvu.jar".**

-----

# How do I get set up? #

### What you'll need: ###

* [FormVu](https://www.idrsolutions.com/formvu/download/) - this provides all the conversion functionality.
* [Maven](https://maven.apache.org/download.cgi) - used to build the .war file.

### Trial Version: ###

If you do not have a full verison of FormVu you can get the trial version from [here](https://www.idrsolutions.com/formvu/trial-download/).

### Build: ###

Clone a copy of the formvu-microservice-example git repo:

```
git clone git://github.com/idrsolutions/formvu-microservice-example
```

Add the FormVu jar to the project by copying it into the /lib directory. Make sure that it is named "formvu.jar"

Open up a terminal / command prompt window in the base directory of the formvu-microservice-example project, and build the .war file by running the command:
```
mvn compile war:war
```

This will generate the formvu-micrservice.war file inside the /target directory.

### Deployment: ###

See our [application server tutorials](https://support.idrsolutions.com/formvu/tutorials/cloud/app-server/) for instructions on deployment.

See our [cloud platform tutorials](https://support.idrsolutions.com/formvu/tutorials/cloud/docker/) for instructions on cloud deployment.

### docker-compose deployment: ###

Docker usage can be found in its own project [here](https://github.com/idrsolutions/formvu-docker).

-----

### Usage: ###

You can interact with the FormVu Microservice Example using the REST API.

A complete list of raw requests that can be used can be found [here](/API.md).

For specific languages, see our tutorials on how to [run FormVu from other languages](https://support.idrsolutions.com/formvu/tutorials/cloud/languages/).

-----

# Who do I talk to? #

Found a bug, or have a suggestion / improvement? Let us know through the Issues page.

Got questions? You can contact us [here](https://idrsolutions.my.site.com/s/request).

-----

Copyright 2023 IDRsolutions

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
