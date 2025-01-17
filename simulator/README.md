# Corda 5 Simulator

Corda 5 Simulator is a lightweight testing / demo tool that simulates a Corda 5 network, enabling you to run Cordapps,
demonstrate realistic behaviour and get feedback on how they are likely to behave with a real Corda network.

Simulator does not verify identity, check permissions, keep anything secure or encrypted, suspend / restart
flows or handle more than one version of any Flow.  It is intended only for low-level testing (meaning, providing
quick feedback and documenting examples of how things work) or demoing CorDapps. For full testing, use a real or
production-like implementation of Corda.

The main class for starting your CorDapps is `net.corda.simulator.Simulator`. "Uploading" your flow for a given party 
will create a simulated "virtual node" which can then be invoked using the initiating flow class (in a real Corda
network this would be done using the `CPI_HASH`).

```kotlin
  val corda = Simulator()
  val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
  val holdingIdentity = HoldingIdentity.create(member)
  val node = corda.createVirtualNode(holdingIdentity, HelloFlow::class.java)

  val response = node.callFlow(
      RequestData.create("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
  )
```

Simulator will wire up your flow with lightweight versions of the same injected services that you'd get with
the real Corda, enabling your flows to communicate with each other, persist data (currently to an in-memory database)
and "sign" data (see below).

## RequestData

Corda normally takes requests via its API in the form of JSON-formatted strings, which are converted
by Corda into an `RPCRequestData` interface. This is represented in Simulator by a `RequestData` factory,
which allows Simulator to construct an `RPCRequestData` when the flow is called. There are three different construction
methods available:

- A JSON-formatted string, as you would submit with `curl`:

```kotlin
val jsonInput = """
{
  "httpStartFlow": {
    "clientRequestId": "r1",
    "flowClassName": "${CalculatorFlow::class.java.name}",
    "requestData":  "{ \"a\" : 6, \"b\" : 7 }"
  }
}
""".trimIndent()
val requestBody = RequestData.create(jsonInput)
```

- A three-part constructor with the request and flow classname separately, as you would submit through
  Swagger UI:

```kotlin
val requestBody = RequestData.create(
    "r1", 
    "${CalculatorFlow::class.java.name}",
    "{ \"a\" : 6, \"b\" : 7 }"
)
```

- A three-part constructor that is strongly typed:

```kotlin
val requestBody = RequestData.create(
    "r1", 
    CalculatorFlow::class.java, 
    InputMessage(6, 7)
)
```

## Instance vs Class upload

Simulator has two methods of creating nodes with responder flows:
- via a flow class, which will be constructed when a response flow is initialized.
- via a flow instance, which must be uploaded against a protocol.

Uploading an instance allows flows to be constructed containing other mocks, injected logic, etc. It also
allows mocks to be used in place of a real flow. For instance, using Mockito:

```kotlin
val responder = mock<ResponderFlow>()
whenever(responder.call(any())).then {
    val session = it.getArgument<FlowSession>(0)
    session.receive<RollCallRequest>()
    session.send(RollCallResponse(""))
}

val node = corda.createVirtualNode(
    HoldingIdentity.create(MemberX500Name.parse(studentId)),
    "roll-call",
    responder
)
```

Note that uploading an instance of a flow bypasses all the checks that Simulator would normally carry out on
the flow class.

## Key Management and Signing

In real Corda, an endpoint is available for generating keys with different schemes and different HSM categories.
Currently, only signing with ledger keys is supported. These can be accessed through `MemberInfo` available in 
the `MemberLookup` service.

In Simulator, keys can be generated via a method on the virtual node:

```kotlin
val publicKey = node.generateKey("my-alias", HsmCategory.LEDGER, "CORDA.ECDSA.SECP256R1")
```

Simulator's `SigningService` mimics the real thing by wrapping the bytes provided in a readable JSON wrapper, using
the key, alias, HSM category and signature scheme.

```json
{
  "clearData":"<clear data bytes>",
  "encodedKey":"<PEM encoded public key>",
  "signatureSpecName":"<signature spec name>",
  "keyParameters":{"alias":"<alias>","hsmCategory":"LEDGER","scheme":"<scheme>"}
}
```

The equivalent `DigitalVerificationService` simply looks to see if the clear data, signature spec and key are a match.

Note that as with real Corda, private keys are contained within their own node, so keys generated in one 
node cannot be used to sign data in another (though all public keys are accessible through `MemberInfo`).

Note also that Simulator does not check to see if any given scheme is supported, and will only
ever generate an ECDSA key, regardless of parameters. To verify that your chosen key scheme and signature spec
are supported and work together, test using a real Corda deployment.

> **⚠ Warning**
> 
> Simulator never actually encrypts anything, and should not be used with sensitive data or in a production
> environment.

## Standalone tools and services

Simulator has some components which can also be used independently:

- A `FlowChecker` which checks your flow for a default constructor and required Corda annotations.
- A `JsonMarshallingService` which can be used to convert objects to JSON and vice-versa, available through the  
  `JsonMarshallingServiceFactory`.

## TODO:

- Check for @CordaSerializable on messages
- Handle errors for unmatched sends / receives
- Implement FlowMessaging send / receive methods
- Allow upload and invocation of InitiatingFlow instances
- Timeouts