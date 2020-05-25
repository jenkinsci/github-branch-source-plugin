.PHONY: test clean-install all

all: test clean-install

test:
	docker run -it --rm --name my-maven-project -v "$(shell pwd)":/usr/src/mymaven -w /usr/src/mymaven maven:3-jdk-8 mvn test

clean-install:
	docker run -it --rm --name my-maven-project -v "$(shell pwd)":/usr/src/mymaven -w /usr/src/mymaven maven:3-jdk-8 mvn clean install