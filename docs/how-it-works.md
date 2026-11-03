# How JavaChat Works

This guide walks through the whole project, from the moment someone opens the page to the moment a reply lands in the database. It is written for anyone who wants to learn from the code, including people who are new to Java, Spring or AI apps. If you come from .NET you will see a few comparisons along the way, because that is where this project started.

Read it top to bottom the first time. Later you can jump to whichever part you need.

## What the app does

JavaChat is a single chat window. You sign in with Google, you talk to an assistant, and the assistant remembers you. It remembers what you said a few messages ago, it keeps a short summary of older parts of the conversation, and it stores lasting facts about you so it can bring them up days later in a fresh chat.

Language models remember nothing on their own. Every request to a model starts from a blank page, and a chatbot that seems to remember you is really being handed the right context each time. Most of this project is about choosing that context well and keeping it private to each user.

## The pieces

The backend is a Spring Boot 4 application written in Java 21. It talks to a PostgreSQL 18 database that has the pgvector extension installed, and it calls two models on Azure OpenAI. One model (gpt-4.1-mini) writes the replies. The other (text-embedding-3-small) turns text into lists of numbers so the app can search by meaning.

Everything except the model calls runs on our own machine or server. The database lives in Docker and the app runs from an IDE during development. For production the plan is to run the app in its own container on a Debian server, with a Cloudflare Tunnel forwarding traffic from the public domain to port 8080.

```
Browser ──► Spring Boot app ──► Azure OpenAI (chat and embeddings)
                  │
                  └──► PostgreSQL + pgvector (users, messages, summaries, memories)
```

The Java code is split by feature rather than by layer. Each folder under `com.javachat` holds one area of the app.

```
com.javachat
 ├── config    security, error handling, async support
 ├── user      the User entity, Google login hook, /api/me
 ├── chat      messages, history, streaming, summaries, rate limiting
 ├── memory    long term memories and the tool the model uses to save them
 └── account   deleting an account and everything that belongs to it
```

## Running it on your machine

You need Java 21 and Docker. Maven comes bundled through the wrapper script `mvnw`, so installing it separately is optional.

Secrets never go into the repository. They live in a file called `.env` in the project root, which git ignores. Copy `.env.example` to `.env` and fill it in with a database password of your choice, a Google OAuth client ID and secret, and the endpoint and key of an Azure OpenAI resource. The Google client needs `http://localhost:8080/login/oauth2/code/google` as an authorised redirect URI.

Start the database first and then the app.

```bash
docker compose up -d db
./mvnw spring-boot:run
```

On Windows PowerShell the second command is `.\mvnw spring-boot:run`. The app listens on port 8080, and the first start creates every table through Flyway.

Docker builds the database image from `docker/postgres/Dockerfile`. The official Postgres Alpine image does not ship with pgvector, so the Dockerfile compiles it in and removes the build tools afterwards. A small SQL script in `docker/postgres/init` switches on the extensions the first time the data volume is created.

## How Spring Boot holds everything together

If Spring is new to you, this part makes the rest of the guide much easier.

Spring builds your objects for you. When a class carries an annotation such as `@Service`, `@Component`, `@RestController` or `@Configuration`, Spring finds it while scanning the `com.javachat` package at startup, creates exactly one instance of it, and keeps it around. These managed objects are called beans. When one bean needs another, it simply asks for it in its constructor and Spring passes it in. That is dependency injection, and every class in this project uses it the same way.

```java
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
```

Nobody writes `new UserService(...)` anywhere. Spring works out the order on its own, because it can see from the constructors which bean needs which.

The other half of the magic is auto configuration. Spring Boot looks at the libraries on the classpath and the settings in `application.yml`, and from those it creates ready made beans. Because the PostgreSQL driver is present and a database URL is configured, you get a connection pool. Because the Spring AI OpenAI starter is present and an endpoint is configured, you get a client that talks to Azure. You rarely configure these objects by hand.

Coming from .NET, a bean is roughly a singleton registered in the service collection, except the registration happens through annotations and package scanning instead of lines in `Program.cs`. Interfaces are only introduced in this project where there really are several implementations. Services are plain classes, because Spring and Mockito can both work with concrete classes directly.

## Configuration

`src/main/resources/application.yml` is the single place where behaviour is configured. Values written as `${NAME}` or `${NAME:default}` are filled in from the `.env` file locally and from real environment variables inside Docker. The line `spring.config.import: optional:file:.env[.properties]` is what lets Spring read the `.env` file directly, and the word `optional` keeps the container from failing when the file is absent.

A few settings deserve a mention. `ddl-auto: validate` tells Hibernate to check the tables but never change them. `open-in-view: false` stops a database session from being held open for the whole web request. `forward-headers-strategy: framework` makes the app trust the `X-Forwarded-*` headers that a proxy or tunnel adds, which matters for building the correct `https` redirect URL for Google. `spring.mvc.problemdetails.enabled` turns every error into a standard JSON body, and `spring.mvc.async.request-timeout` gives streamed replies up to two minutes to finish.

## The database

### Tables are created by Flyway

The schema is written as plain SQL files in `src/main/resources/db/migration`. Flyway runs them in order when the app starts and records each one in a table called `flyway_schema_history`, so every file runs exactly once on every machine.

The file names follow a strict pattern, a capital V, a version number, two underscores and a description. A single underscore is enough to make Flyway silently skip the file, which happened once during development and is worth remembering.

There are four migrations. V1 creates `users`, V2 creates `chat_messages`, V3 creates `vector_store` for long term memories, and V4 creates `conversation_summaries`. Once a migration has run you never edit it again, because Flyway stores a checksum and refuses to start if an applied file has changed. Changes always come as a new file with the next version number.

### JPA, Hibernate and Spring Data

These three names confuse almost everyone at first. JPA is a specification, a set of annotations and interfaces describing how Java objects map to tables. Hibernate is the library that implements that specification and does the real work of generating SQL. Spring Data JPA sits on top and writes repository classes for you.

An entity is a class mapped to a table. `User` is a good example.

```java
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false, unique = true, updatable = false)
    private String googleSub;
    ...
}
```

The ID is a `Long` with a capital L because it has to be able to be empty before the row is saved, and because Java generics only accept classes, never primitive types such as `long`. The table uses `BIGSERIAL`, a 64 bit number generated by the database. Random UUIDs were considered and rejected for these tables, since the app relies on IDs growing in insert order and never accepts IDs from the client anyway.

Entities in this project have no setters. A user changes through `recordLogin(...)`, a method that updates the name, email, picture and last login time together, so every change to a user happens in one deliberate place.

A repository is just an interface.

```java
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleSub(String googleSub);
}
```

Nobody implements it. At startup Spring Data creates a proxy object that implements the interface. The built in methods such as `save`, `findById` and `deleteById` come from a class inside Spring Data, and custom methods are generated by reading their names. `findByGoogleSub` becomes a query with `WHERE google_sub = ?`. A typo in such a name makes the app refuse to start, which is a helpful early warning. When a name would get too long, the method gets a `@Query` with the query written out instead, as `ChatMessageRepository.findBetween` does.

`Optional<User>` is how Java says a value may be missing. Instead of returning `null`, the method returns a box that is either empty or holds the user, and the caller has to decide what to do in each case.

### Transactions and dirty checking

A method marked `@Transactional` runs inside one database transaction. Everything it does is committed together when it returns, or rolled back together if it throws.

Inside a transaction Hibernate also tracks every entity it loaded. If you change one of those objects, the change is written to the database at commit time without any call to `save`. That is called dirty checking, and it is why `UserService.syncGoogleUser` only calls `save` for brand new users. Returning users are updated simply by calling `recordLogin` on the loaded object.

Three rules about transactions shape this codebase. Calls to Azure never happen inside a transaction, because a slow network call would hold a database connection the whole time. The annotation only works when the method is called from another bean, since Spring applies it through a proxy that wraps the bean. And only unchecked exceptions trigger a rollback by default.

## Signing in with Google

### The flow

The app uses the server side authorization code flow, which Spring Security implements almost entirely on its own. The browser never sees a Google token.

When a signed out person opens `/oauth2/authorization/google`, Spring redirects them to Google. After they sign in, Google sends the browser back to `/login/oauth2/code/google` with a one time code. Spring trades that code for tokens in a direct server to server call that includes the client secret, then checks the ID token's signature, issuer, audience and expiry. If everything is valid, Spring creates a session and the browser receives a `JSESSIONID` cookie. From then on that cookie is the login.

This is different from a single page app that collects a Google token in the browser and sends it to an API, which is how many .NET projects do it. Here there is no token handling in our code and nothing to refresh, and the session cookie is marked HttpOnly, so JavaScript cannot read it.

### The filter chain

Every request passes through a chain of Spring Security filters before it reaches a controller. `SecurityConfig` does not write filters. It configures them, in roughly the same spirit as adding middleware in ASP.NET Core.

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
        .requestMatchers("/actuator/health", "/error").permitAll()
        .anyRequest().authenticated())
    .exceptionHandling(exceptions -> exceptions
        .defaultAuthenticationEntryPointFor(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
            pathPattern("/api/**")))
    .oauth2Login(oauth -> oauth
        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService)))
    .csrf(csrf -> csrf.spa())
    .logout(logout -> logout
        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
```

The page shell and static files are public, because the landing page has to load before anyone can click the login button. Everything else requires a session. Requests under `/api/**` get a plain 401 when nobody is signed in, instead of the usual redirect to Google, since a JavaScript `fetch` cannot do anything useful with a login page. Logout answers with 204 so the frontend can clear its state without a page reload.

### Saving the user on login

`GoogleOidcUserService` hooks into the moment right after Google has been verified. It lets Spring's default service build the signed in user first, rejects the login if Google says the email is not verified, and then calls `UserService.syncGoogleUser`, which either creates a new row or updates the existing one.

Users are matched on Google's `sub` claim rather than on email. The `sub` is a permanent identifier that never changes, while people do change their email addresses.

Spring constructs `GoogleOidcUserService` because it is annotated with `@Component`, passes it into the security configuration as a method parameter, and calls its `loadUser` method on every login. Our code never calls it directly.

### Identity always comes from the session

Every controller gets the signed in user the same way.

```java
@GetMapping("/me")
public UserResponse me(@AuthenticationPrincipal OidcUser principal) {
    User user = userService.getByGoogleSub(principal.getSubject());
    return UserResponse.from(user);
}
```

No endpoint accepts a user ID from the URL or the request body. That single rule is what makes per user memory safe, because nobody can ask for someone else's data by changing a number.

### CSRF

Because the login is a cookie, browsers attach it automatically, even when another website triggers the request. CSRF protection blocks that by requiring a secret token on every state changing request such as POST or DELETE. The setting `csrf.spa()` puts that token in a cookie called `XSRF-TOKEN`, which our own JavaScript can read and send back in an `X-XSRF-TOKEN` header. A request without the header gets a 403.

## Talking to the model

### What Spring AI does

Spring AI is a translator between Java code and AI services. Instead of building the HTTP request for Azure by hand, signing it, sending it and parsing the JSON answer, the code says this.

```java
chatClient.prompt()
    .system(systemPrompt)
    .messages(history)
    .user(userMessage)
    .call()
    .content();
```

Under the `ChatClient` sits a `ChatModel`, which Spring AI builds from the `spring.ai.openai` settings. Spring AI 2.0 reaches Azure through its regular OpenAI starter with `microsoft-foundry: true`, since the separate Azure starter was dropped in that version.

The project was first written against AWS Bedrock and Claude. The AWS account got stuck in verification, and Claude is not offered on Azure student subscriptions, so the app moved to Azure OpenAI. That move only touched `pom.xml` and `application.yml`. The Java code stayed the same, which is exactly the point of using an abstraction like Spring AI.

### The system prompt

The system prompt is a set of instructions sent with every request that the user never sees. It names the assistant, tells it not to call itself ChatGPT, tells it to save lasting facts with its memory tool, and asks for short clear answers.

A plain line saying "You are JavaChat" was not enough on its own, because the model kept introducing itself as ChatGPT. Writing the rule as a concrete trigger worked better, so the prompt now says what to do when someone asks who it is. Specific situations beat vague labels when you write prompts.

The prompt is built fresh for every request by `buildSystemPrompt`, because the summary and the recalled memories differ from one message to the next.

## Memory

There are three layers, and each one solves a different problem.

### The recent window

Every message is stored in `chat_messages`, forever, but only the most recent ones are sent to the model. `ChatService.loadWindow` takes the latest 20 messages and keeps them, newest first, until their combined length passes 8,000 characters. Then it reverses them into reading order.

The query sorts newest first because that is how SQL picks the latest rows, and the reversal exists because a conversation has to be read from oldest to newest. A character budget is used instead of a fixed count so that a few very long messages cannot make each request expensive.

This window is what lets the assistant follow a conversation. A message such as "tell me more about the second one" only makes sense when the previous answer is right there in the request.

### The rolling summary

Messages that fall out of the window are not lost. `SummaryService` keeps one short summary per user in `conversation_summaries`, along with the ID of the last message it covers.

After each reply, `ChatService` hands the ID of the oldest message in the window to `SummaryService.updateIfNeeded`. That method looks for messages between the last summarised one and the start of the window. If there are at least ten, it asks the model to fold them into the existing summary in under 150 words, saves the new text and moves the marker forward. The summary is added to the system prompt of every later request.

The method is annotated with `@Async`, which together with `@EnableAsync` in `AsyncConfig` makes it run on a background thread. The user gets their reply at normal speed and the summary is ready for the next message. If the summarising call fails, the error is only logged and the next batch tries again.

### Long term memories

Facts that matter beyond a single conversation, such as a person's job, goals or preferences, are stored as embeddings in `vector_store`.

An embedding is a list of numbers that represents the meaning of a piece of text. Texts with similar meaning get similar numbers even when they share no words, so "preparing for a Levi9 interview" and "what should I study for my job interview" end up close together while "I have a cat" lands far away. The embedding model used here produces 1536 numbers per text, which is why the column is `VECTOR(1536)` and why the same number appears in `application.yml`. Those three places must always agree.

pgvector stores those lists and finds the ones closest to a query. Closeness is measured with cosine distance, and an HNSW index keeps the search fast without comparing against every row.

Spring AI's `VectorStore` hides all of this. `MemoryService.remember` creates a `Document` with the fact as its text and the user's ID in its metadata, and adding it makes Spring AI call Azure for the embedding and insert the row. `MemoryService.recall` searches by meaning for the five closest memories, and it always filters on the user's ID.

```java
SearchRequest request = SearchRequest.builder()
    .query(query)
    .topK(RECALL_LIMIT)
    .filterExpression(new FilterExpressionBuilder().eq(USER_ID_KEY, userId).build())
    .build();
```

That filter is the wall between users. The metadata stores the ID as a JSON number and the filter compares it with a `Long`, and the two types have to match, otherwise the filter quietly finds nothing.

Recalling memories and adding them to the prompt before the model answers is what people call retrieval augmented generation, or RAG.

### How the model saves memories

The model decides what is worth remembering. It does that by calling a tool, a Java method it is allowed to request.

```java
@Tool(description = """
        Save an important, long-lasting fact about the user so you can remember it in future conversations.
        ...
        """)
public String saveMemory(@ToolParam(description = "...") String fact, ToolContext toolContext) {
    ...
    Long userId = (Long) toolContext.getContext().get(USER_ID_CONTEXT_KEY);
    memoryService.remember(userId, fact.strip());
    return "Saved.";
}
```

The model never runs code. It answers with a request to call `saveMemory` with some text, Spring AI runs the method, sends the result back, and the model continues writing its reply. All of that happens inside the single call from `ChatService`.

The descriptions on `@Tool` and `@ToolParam` are the only thing the model knows about the tool, so they work like a small prompt that explains when to use it and how to phrase the fact.

The user's ID reaches the tool without the model ever seeing it. `ChatService` attaches it to the request with `.toolContext(...)`, Spring AI keeps that map on our side, and passes it to the method as a `ToolContext` parameter, which it leaves out of the description sent to Azure. The model chooses the fact. Our code chooses whose memory it is.

The tool also checks its input like any other API, rejecting blank text or anything over 500 characters, and it answers with a message instead of throwing so the model can react.

### Seeing and deleting memories

`GET /api/memories` lists a user's memories and `DELETE /api/memories/{id}` removes one. `VectorStore` has no "list everything for this user" operation, so these two use `JdbcTemplate` with plain parameterised SQL, which is the closest thing Java has to Dapper.

The delete statement includes the user's ID in its `WHERE` clause. If someone sends the ID of another person's memory, zero rows match and the answer is 404, which reveals nothing about whether that memory exists.

## A full chat request

This is what happens when someone sends a message through `POST /api/chat`.

```
① Security filters    session cookie gives the signed in user, CSRF header checked
② ChatController       rate limit, user from the session, @Valid checks the message
③ ChatService.prepare  recent window, recalled memories, summary, system prompt,
                        tool and tool context, all built into a request that is not sent yet
④ The model call       the request goes to Azure, the model may call saveMemory on the way
⑤ saveExchange         both messages saved in one transaction, summary check started in the background
⑥ Response             the saved assistant message as JSON
```

`prepare` and `saveExchange` came out of a refactor. They are shared by the normal endpoint and the streaming endpoint, which differ only in how the prepared request is sent.

## Streaming replies

### Why

With a normal call the model writes its whole answer inside Azure, and Azure only replies when it is done, so the user stares at an empty screen for a few seconds. A model actually produces its answer in small pieces called tokens, usually a word or part of one, and Azure can forward each token as soon as it exists. `POST /api/chat/stream` passes those pieces straight on to the browser, so text starts appearing within half a second.

### Server-Sent Events

The pieces travel as Server-Sent Events. The HTTP response stays open with the content type `text/event-stream`, and the server writes small events into it over time. Each event is a few lines of text followed by a blank line.

```
event:token
data:{"text":"Spring"}

event:token
data:{"text":" Boot"}

event:done
data:{"id":10,"role":"ASSISTANT","content":"Spring Boot is ...","createdAt":"..."}
```

There are three kinds of events. `token` carries a piece of text, `done` carries the saved message once everything is finished, and `error` carries a problem description if something breaks halfway.

Tokens are wrapped in a tiny JSON object on purpose. Spring writes `data:` with the value directly after it, and many tokens begin with a space. Plain text would lose that space in most event readers and glue words together, and a newline inside a token would break the event apart. JSON keeps both safe.

### What a Flux is

`ChatClient` returns the streamed answer as a `Flux<String>`. A Flux comes from the Reactor library and stands for values that arrive over time. Unlike a `List`, whose items all exist already, a Flux hands you each value as it appears, and at the end it either completes or fails.

The most important thing about a Flux is that writing one does nothing. Code that builds a Flux only describes a plan. The plan runs when something subscribes to it, and in this app the subscriber is Spring MVC, after the controller returns the Flux.

You shape the plan with operators. `map` turns each value into another value. `doOnNext` performs a side effect with each value and passes it on unchanged. `concatWith` continues with another stream once the first one completes. `onErrorResume` replaces the rest of the stream with something else if an error occurs.

A `Mono` is the same idea for at most one value. `Mono.just(x)` wraps a value you already have. `Mono.fromCallable(() -> ...)` wraps code and only runs it once the Mono is subscribed to, which makes it the right tool for work that has to wait.

### The streaming method

```java
public Flux<ServerSentEvent<Object>> replyStream(User user, String userMessage) {
    PreparedChat prepared = prepare(user, userMessage);
    StringBuilder fullReply = new StringBuilder();

    Flux<ServerSentEvent<Object>> tokens = prepared.request().stream().content()
        .doOnNext(fullReply::append)
        .map(token -> event("token", new ChatStreamToken(token)));

    Mono<ServerSentEvent<Object>> done = Mono
        .fromCallable(() -> saveExchange(user, userMessage, fullReply.toString(), prepared.window()))
        .subscribeOn(Schedulers.boundedElastic())
        .map(saved -> event("done", ChatMessageResponse.from(saved)));

    return tokens
        .concatWith(done)
        .onErrorResume(error -> {
            log.error("Streaming reply failed", error);
            return Mono.just(event("error", ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The assistant is unavailable right now. Please try again shortly.")));
        });
}
```

Every token flows past the `StringBuilder` and gets appended, while `map` turns the same token into an event for the browser. When the model finishes, `concatWith` starts the `done` part, which saves the complete text and turns the saved message into the final event.

The save is wrapped in `fromCallable` because at the moment that line is reached no tokens exist yet. Saving right away would store an empty reply. The `concatWith` ordering guarantees that the save only runs after the last token has gone by.

`subscribeOn(Schedulers.boundedElastic())` exists because saving with JPA blocks while it waits for Postgres. The tokens arrive on network threads that serve every stream in the app and must never sit and wait, otherwise other users' streams would freeze. `boundedElastic` is Reactor's pool for blocking work. It grows when needed up to a limit of ten threads per CPU core and shrinks again when idle. Whenever blocking code ends up inside a reactive pipeline, the pattern is `Mono.fromCallable(...)` followed by `subscribeOn(Schedulers.boundedElastic())`.

Here is the timeline of one streamed reply.

```
0.00 s  request thread   prepare() runs now, the plan is built and returned
                         Spring subscribes and the request to Azure goes out
0.40 s  network thread   "Spring" is appended and sent as a token event
0.45 s  network thread   " Boot" is appended and sent
  ...
3.80 s  network thread   Azure says the answer is complete, concatWith starts the done part
3.80 s  boundedElastic   both messages are saved, the summary check starts
3.82 s  boundedElastic   the done event is sent and the response closes
```

### When things go wrong

Rate limits, validation errors and missing logins are all checked before the stream starts, so those still come back as ordinary JSON error responses with the right status code. Once the first event has gone out the status line has already been sent as 200, so a failure in the middle becomes an `error` event instead. Nothing partial is ever saved. If the browser disconnects, Spring cancels the subscription, the request to Azure stops and nothing is saved either.

The normal `POST /api/chat` endpoint is kept next to the streaming one. Both produce the same saved message, and the plain version is simpler for tests, scripts and debugging.

### Reading the stream in the browser

The built in `EventSource` only supports GET requests without headers, so the frontend uses `fetch` and reads the response body piece by piece. It decodes the bytes into text, splits on blank lines to separate events, keeps any unfinished piece for the next round, and reads the `event:` and `data:` lines of each event. A `token` gets appended to the bubble on screen, and `done` replaces that temporary bubble with the saved message, which has exactly the same shape as messages loaded from history.

## Rate limiting

Each chat message costs money, since it triggers an embedding, a model call and sometimes a second embedding for a saved memory. `ChatRateLimiter` keeps any single user from sending too many.

It uses the token bucket idea. Picture a bucket holding up to ten tickets. Sending a message spends one ticket, and tickets drip back in steadily at one every six seconds until the bucket is full again. A user can fire off ten quick messages, and after that they can only go as fast as the tickets return. A second bucket holds 200 tickets that refill over a day, which caps the daily cost per person. A message needs a ticket from both.

The library Bucket4j implements this. `refillGreedy` makes tickets return continuously instead of all at once at the end of each minute, so someone who hits the limit waits seconds rather than a full minute. The refill is not done by a timer. Each time a request arrives, the bucket works out how much time has passed and adds the tickets that would have dripped in.

```java
Bucket bucket = buckets.computeIfAbsent(userKey, key -> newBucket());
ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

if (!probe.isConsumed()) {
    long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
    throw new RateLimitExceededException(retryAfterSeconds);
}
```

The probe is the bucket's answer. `isConsumed()` decides everything, true means a ticket was taken and the message may go ahead. The wait time is only used to tell the user how long to hold off. It comes in nanoseconds and gets rounded up to whole seconds, so nobody retries a moment too early.

Each user has their own bucket, kept in a `ConcurrentHashMap` keyed by their Google `sub`. Tomcat handles many requests at the same time on different threads, and all of them share the single limiter that Spring created. A plain `HashMap` is not built for that and can lose entries or create two buckets for the same person. `ConcurrentHashMap` is safe under that load, and its `computeIfAbsent` guarantees a new user gets exactly one bucket.

The buckets live in memory, so they reset when the app restarts, and running two copies of the app would give each user twice the allowance. Moving the buckets into Redis would fix both if the app ever needs to scale out.

When the limit is reached, the response is a 429 with a `Retry-After` header and the same number in the JSON body.

## Errors

`GlobalExceptionHandler` is annotated with `@RestControllerAdvice`, which makes it handle exceptions from every controller in one place. It extends Spring's `ResponseEntityExceptionHandler`, so all of Spring's own errors such as unknown URLs, wrong methods and malformed JSON already come out as problem details.

Every error therefore has the same JSON shape, defined by RFC 9457.

```json
{ "type": "about:blank", "title": "Service Unavailable", "status": 503,
  "detail": "The assistant is unavailable right now. Please try again shortly.", "instance": "/api/chat" }
```

Validation failures add an `errors` object naming each field and what was wrong with it. A rate limit from our own limiter becomes 429. A rate limit coming from Azure also becomes 429 with a different message. Any other failure from the Azure SDK becomes 503, which tells the client that a service we depend on is down rather than that our code has a bug. Anything unexpected becomes a 500 with a generic message, and the full stack trace only ever goes to the server log.

Spring picks the most specific handler for each exception, which is why the Azure rate limit gets its own answer even though it is also a general Azure exception.

Security errors such as a missing login or a bad CSRF token happen in the filter chain before any controller runs, so this class never sees them. They already come out as clean 401 and 403 responses.

Java exceptions cost something to create, mostly because of the stack trace, but next to a database query or a model call that cost is tiny. Spring leans on exceptions because transactions roll back on them and Spring Security relies on them, so the project follows that style. Values that may simply be missing use `Optional` instead.

## History, new conversations and accounts

`GET /api/chat/history` returns the 50 newest messages in reading order. Adding `?before=812` returns the 50 messages just before message 812, which lets the interface load older messages as you scroll up. Pagination by cursor is used instead of page numbers because new messages would otherwise shift the pages around.

`DELETE /api/chat/history` starts a new conversation. It removes the messages and the summary in one transaction with a single bulk `DELETE` statement, and it keeps long term memories, so the assistant still knows who you are afterwards.

`DELETE /api/me` removes an account completely. Messages and the summary disappear automatically through `ON DELETE CASCADE` on their foreign keys. Memories have no foreign key, because the user ID sits inside JSON metadata, so `AccountService` deletes them explicitly before deleting the user, all inside one transaction. The session is ended right after with `request.logout()`. This lives in its own `account` package so that the user and memory packages do not end up depending on each other.

## The API at a glance

Every endpoint except the health check requires a signed in session, and every POST or DELETE needs the CSRF header.

| Method and path | What it does |
|---|---|
| `GET /api/me` | The signed in user's email, name and picture |
| `DELETE /api/me` | Deletes the account and all its data, then logs out |
| `POST /api/chat` | Sends a message and returns the saved reply as JSON |
| `POST /api/chat/stream` | Sends a message and streams the reply as events |
| `GET /api/chat/history?before={id}` | Fifty messages, newest page first, in reading order |
| `DELETE /api/chat/history` | Clears the conversation and summary, keeps memories |
| `GET /api/memories` | Lists long term memories |
| `DELETE /api/memories/{id}` | Deletes one memory |
| `POST /logout` | Ends the session, answers 204 |
| `GET /actuator/health` | Health check, public |

Interactive documentation generated by springdoc is available at `/swagger-ui.html` after signing in, with the raw OpenAPI description at `/v3/api-docs`.

## Design rules worth keeping

The person making a request is always taken from the session and never from anything the client sends. Authorisation lives in Java and SQL, never in the prompt, because a prompt can be talked out of its instructions and a `WHERE` clause cannot. The model gets exactly one narrow tool and no way to choose whose data it touches, so the worst a malicious prompt can do is affect that user's own conversation. Slow network calls stay outside database transactions, and optional slow work such as summaries runs in the background. Entities never leave the server, only small records built for the response do. The schema is versioned in SQL and Hibernate only checks it.

Prompt injection itself cannot be fully prevented with today's models. The design aims for containment instead, so that a successful injection has nowhere harmful to go.

## Notes for .NET developers

A few Java habits trip people up when they arrive from C#.

Method names start with a lowercase letter, so it is `prompt()` and not `Prompt()`. The folder a file lives in has to match its `package` line, and a mismatch is a real error rather than a style issue. Spring only scans packages under `com.javachat`, so a class outside it is silently ignored.

`super(...)` calls the parent class's constructor, the same as `: base(...)`, and it has to be the first line. Java records work much like C# records, but their accessors drop the `get` prefix, so you write `request.message()`, and they are always immutable. They cannot be JPA entities.

There is no `async` and `await` in this backend. JPA calls simply block, which is normal in Spring MVC because every request gets its own thread. Java 21's virtual threads make that blocking cheap if the app ever needs to handle many more requests. Reactive code with Flux and Mono only appears where streaming truly needs it.

Streams play the role of LINQ. A list has no `map` method, so you call `.stream()` first, chain `map` and `filter`, and finish with `.toList()`.

## What is still to come

Automated tests are next, covering services with Mockito, repositories against a real Postgres with pgvector through Testcontainers, the security rules and the rate limiter. After that comes a React frontend built with Vite that talks to this API, starting with the communication layer and leaving styling for last. The final step is deployment to a Debian server in Docker behind a Cloudflare Tunnel, followed by an updated README with screenshots.
