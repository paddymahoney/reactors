package tutorial



import io.reactors._
import org.scalatest._
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.duration._



/*!md
## Reactors

As we learned previously, event streams always propagate events on a single thread.
This is useful from the standpoint of program comprehension, but we still need a way
to express concurrency in our programs. In this section, we will see how this is done
with reactors.

A reactor is the basic unit of concurrency. For readers familiar with the actor model,
a reactor is close to the concept of an actor. While actors receive messages, reactors
receive events. However, while an actor in particular state has only a single point in
its definition where it can receive a message, a reactor can receive an event from
many different sources at any time. Despite this flexibility, one reactor will always
process **at most one** event at any time. We say that events received by a reactor
*serialize*, similar to how messages received by an actor are serialized.

To be able to create new reactors, we need a `ReactorSystem` object, which tracks
reactors in a single machine.
!*/
class Reactors extends FunSuite with Matchers {

  /*!begin-code!*/
  val system = new ReactorSystem("test-system")
  /*!end-code!*/

  test("simple reactor") {
    /*!md
    Before we can start a reactor instance, we need to define its template. One way to
    do this is to call `Reactor.apply[T]` method, which returns a `Proto` object for the
    reactor. The following reactor prints all the events it receives to the standard
    output:
    !*/

    val received = Promise[String]()
    def println(x: String) = received.success(x)

    /*!begin-code!*/
    val proto: Proto[Reactor[String]] = Reactor[String] { self =>
      self.main.events onEvent {
        x => println(x)
      }
    }
    /*!end-code!*/

    /*!md
    Lets examine this code more closely. The `Reactor.apply` method is called with the
    argument `String`. This means that the reactor encoded in the resulting `Proto`
    object only receives events whose type is `String`. This is the first difference
    with respect to the standard actor model, in which actors can receive messages of
    any type. Events received by reactors are well typed.

    In the reactor model, every reactor can access a special event stream called
    `main.events`, which emits events that the reactor receives from other reactors.
    Since we are declaring an anonymous reactor with the `Reactor.apply` method, we need
    to add a prefix `self.` to access members of the reactor.
    We previously learned that we can call `onEvent` to register callbacks to event
    streams, and we used it in this example to print the events using `println`.

    After defining a reactor template, the next step is to spawn a new reactor. We do
    this by calling the `spawn` method on the reactor system:
    !*/

    /*!begin-code!*/
    val ch: Channel[String] = system.spawn(proto)
    /*!end-code!*/

    /*!md
    The method `spawn` takes a `Proto` object as a parameter. The `Proto` object can
    generally encode the reactor's constructor arguments, scheduler, name and other
    options. In our example, we created a `Proto` object for an anonymous reactor
    with the `Reactor.apply` method, so we don't have any constructor arguments. We will
    later see alternative ways of declaring reactors and configuring `Proto` objects.

    The method `spawn` does two things. First, it registers and starts a new reactor
    instance. Second, it returns a `Channel` object, which is used to send events to
    the newly created reactor. We can show the relationship between a reactor, its
    event stream and the channel as follows:

    ```
      "Hello world!"                            
        |              #-----------------------#
        |              |    Reactor[String]    |
        V              |                       |
    Channel[String] ---o--> Events[String]     |
          ^            |                       |
          |            |                       |
          |            #-----------------------#
       "Hola!"                                  
    ```

    The only way for the outside world to access the inside of a reactor is to send
    events to its channel. These events are eventually delivered to the corresponding
    event stream, which the reactor can listen to. The channel and event stream can only
    pass events whose type corresponds to the type of the reactor.

    Let's send an event to `HelloReactor`:
    !*/

    /*!begin-code!*/
    ch ! "Hola!"
    /*!end-code!*/

    assert(Await.result(received.future, 5.seconds) == "Hola!")

    /*!md
    Running the last statement should print `"Hola!"` to the standard output.
    !*/
  }
}


/*!md
### Defining and configuring reactors

In the previous section, we saw how to define a reactor using the `Reactor.apply`
method. In this section, we take a look at an alternative way of defining a reactor --
by extending the `Reactor` class. Recall that the `Reactor.apply` method defines an
anonymous reactor template. Extending the `Reactor` class declares a named reactor
template.

In the following, we declare `HelloReactor`, which must be a top-level class:
!*/
/*!begin-code!*/
class HelloReactor extends Reactor[String] {
  main.events onEvent {
    x => println(x)
  }
}
/*!end-code!*/


class ReactorsTopLevel extends FunSuite with Matchers {
  val system = new ReactorSystem("test-top-level")

  test("top-level reactor") {
    /*!md
    To run this reactor, we first create a `Proto` object to configure it. The method
    `Proto.apply` takes the type of the reactor and returns a `Proto` object for that
    reactor type. We then call the `spawn` method with that `Proto` object to start the
    reactor:
    !*/

    /*!begin-code!*/
    val ch = system.spawn(Proto[HelloReactor])
    ch ! "Howdee!"
    /*!end-code!*/

    /*!md
    We can also use the `Proto` object to, for example, set the scheduler that the
    reactor instance should use. If we want the reactor instance to run on its
    dedicated thread to give it more priority, we can do the following:
    !*/

    /*!begin-code!*/
    system.spawn(Proto[HelloReactor].withScheduler(
      ReactorSystem.Bundle.schedulers.newThread))
    /*!end-code!*/

    /*!md
    The call to `withScheduler` returns a new `Proto` object that runs on a predefined
    scheduler called `ReactorSystem.Bundle.schedulers.newThread`. A reactor started like
    this is using this scheduler. Reactor systems allow registering custom schedulers.
    In the following, we define a custom `Timer` scheduler, which schedules the reactor
    for execution once every `1000` milliseconds:
    !*/

    /*!begin-code!*/
    system.bundle.registerScheduler("customTimer", new Scheduler.Timer(1000))
    val periodic = system.spawn(Proto[HelloReactor].withScheduler("customTimer"))
    periodic ! "Ohayo!"
    /*!end-code!*/

    Thread.sleep(2000)

    /*!md
    There are several other configuration options for `Proto` objects, listed in the
    online API docs. We can summarize this section as follows -- starting a reactor is
    generally a three step process:

    1. A reactor template is created by extending the `Reactor` class.
    2. A reactor `Proto` configuration object is created with the `Proto.apply` method.
    3. A reactor instance is started with the `spawn` method of the reactor system.

    For convenience, we can fuse the first two steps by using the `Reactor.apply`
    method, which creates an anonymous reactor template and directly returns a `Proto`
    configuration object. Typically, we do this in tests or in the REPL.
    !*/
  }
}


/*!md
### Creating channels

Now that we understand how to create and configure reactors in different ways, we can
take a closer look at channels -- reactor's means of communicating with its environment.
As noted before, every reactor is created with a default channel called `main`, which is
usually sufficient. But sometimes a reactor needs to be able to receive more than just
one type of an event, and needs additional channels for this purpose.
!*/
class ReactorChannels extends FunSuite with Matchers {
}
