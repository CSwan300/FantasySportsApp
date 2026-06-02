# Step 1: Use an official Eclipse Temurin JDK image
FROM eclipse-temurin:21-jdk-jammy

# Step 2: Install sbt inside the container cleanly
RUN apt-get update && apt-get install -y curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add - && \
    apt-get update && apt-get install -y sbt

# Step 3: Set up the application directory
WORKDIR /app

# Step 4: Copy the project files into the container
COPY . /app

# Step 5: Pre-compile the app to cache dependencies
RUN sbt compile

# Step 6: Command to run the application interactively
CMD ["sbt", "run"]