This is the official Mixpanel tracking library for Java. 

Latest Version 
--------------
##### _May 08, 2024_ - [v1.5.3](https://github.com/mixpanel/mixpanel-java/releases/tag/mixpanel-java-1.5.3)
```
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java</artifactId>
    <version>1.5.2</version>
</dependency>
```

You can alternatively download the library jar directly from Maven [here](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.mixpanel%22%20AND%20a%3A%22mixpanel-java%22).

How To Use
----------
The library is designed to produce events and people updates in one process or thread, and
consume the events and people updates in another thread or process. Specially formatted JSON objects
are built by `MessageBuilder` objects, and those messages can be consumed by the
`MixpanelAPI` via `ClientDelivery` objects, possibly after serialization or IPC.

    MessageBuilder messages = new MessageBuilder("my token");
    JSONObject event = messages.event("joe@gribbl.com", "Logged In", null);

    // Later, or elsewhere...
    ClientDelivery delivery = new ClientDelivery();
    delivery.addMessage(event);

    MixpanelAPI mixpanel = new MixpanelAPI();
    mixpanel.deliver(delivery);

Learn More
----------
This library in particular has more in-depth documentation at

    https://mixpanel.com/docs/integration-libraries/java
    
Mixpanel maintains documentation at

    http://www.mixpanel.com/docs


The library also contains a simple demo application, that demonstrates
using this library in an asynchronous environment.

There are also community supported libraries in addition to this library,
that provide a threading model, support for dealing directly with Java Servlet requests,
support for persistent properties, etc. Two interesting ones are at:

    https://github.com/eranation/mixpanel-java
    https://github.com/scalascope/mixpanel-java
    
Other Mixpanel Libraries
------------------------
Mixpanel also maintains a full-featured library for tracking events from Android apps at https://github.com/mixpanel/mixpanel-android

And a full-featured client side library for web applications, in Javascript, that can be loaded
directly from Mixpanel servers. To learn more about our Javascript library, see: https://mixpanel.com/docs/integration-libraries/javascript

This library is intended for use in back end applications or API services that can't take
advantage of the Android libraries or the Javascript library.

License
-------

```
See LICENSE File for details. The Base64Coder class used by this software
has been licensed from non-Mixpanel sources and modified for use in the library.
Please see Base64Coder.java for details.
```
