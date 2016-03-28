# Bolt - The Fastest Way to Build a Server

## Overview

Bolt automates a significant portion of the VSS9 server build process. 

## Running

The following environment variables must be set:
- TOP
- CDS_TOP
- OS
- VIEW
- SERVICE_SUITE_VERSION
- WLS_DIR

For example:
```shell
export TOP=$PWD/TOP
export CDS_TOP=$PWD/CDS_TOP
export OS=LINUX
export VIEW=$PWD
export SERVICE_SUITE_VERSION=95.0.0
export WLS_DIR=$PWD/TOP
```

## Reference
The Java Swing application this was based on is documented here: http://viki.ventyx.us.abb.com/confluence/display/YPRPRJ/Bolt+-+The+Fastest+Way+to+Build+a+Server

Some websites that helped me along the way to get this application running:
- [ScalaFx](http://www.scalafx.org)
- [scalafx/scalafx](https://github.com/scalafx/scalafx)
- [ScalaFX-Tutorials](https://github.com/scalafx/ScalaFX-Tutorials)
- [vigoo/scalafxml](https://github.com/vigoo/scalafxml)
- [ScalaFX with FXML Tutorial](http://vigoo.github.io/posts/2014-01-12-scalafx-with-fxml.html)
- [ScalaFX – Alerts and Dialogs](https://codingonthestaircase.wordpress.com/category/scalafx/)
- [Why I'm getting javafx.fxml.LoadException even the path of the fxml file is correct](http://stackoverflow.com/questions/28844503/why-im-getting-javafx-fxml-loadexception-even-the-path-of-the-fxml-file-is-corr)
- [Javafx error exception in Application start method no controller specified](http://stackoverflow.com/questions/30267459/javafx-error-exception-in-application-start-method-no-controller-specified)
- [JavaFX Dialogs (official)](http://code.makery.ch/blog/javafx-dialogs-official/)
