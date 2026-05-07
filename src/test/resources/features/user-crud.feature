Feature: User CRUD operations
  The CrudController and CrudService should be able to handle all the basic requests made to the test API

  Scenario: Caller wants to create a new User
    Given the caller has the given User:
      | name     | surname  |
      | testUser | sursur   |
    When the POST "/user" endpoint is called
    Then the service returns HTTP 201
    And the new User was created

  Scenario: Caller wants to create a new User, but has a non-blank field as null
    Given the caller has the given User:
      | name     | surname  |
      |          | sursur   |
    When the POST "/user" endpoint is called
    Then the service returns HTTP 400
    And the service returns a message with the validation errors
      | obj     | field | rejectedValue | message         |
      | userDTO | name  | null          | Cannot be blank |

  Scenario: Caller wants to get an already existing User by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    When the GET "/user/{userId}" endpoint is called
    Then the service returns HTTP 200
    And the service returns the User

  Scenario: Caller wants to partially update an already existing User by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the caller has the given User:
      | name          |
      | otherTestName |
    When the PATCH "/user/{userId}" endpoint is called
    Then the service returns HTTP 204
    And the updated User is found in the database

  Scenario: Caller wants to fully replace an already existing User by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the caller has the given User:
      | name          | surname       |
      | otherTestName | otherSurname  |
    When the PUT "/user/{userId}" endpoint is called
    Then the service returns HTTP 204
    And the replaced User is found in the database

  Scenario: Caller wants to delete a User by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    When the DELETE "/user/{userId}" endpoint is called
    Then the service returns HTTP 204
    And the User was removed from the database

  Scenario: Caller wants to get a non-existent User by ID
    Given a random non-existent ID is generated
    When the GET "/user/{randomId}" endpoint is called
    Then the service returns HTTP 404

  Scenario: Caller wants to partially update a non-existent User by ID
    Given a random non-existent ID is generated
    And the caller has the given User:
      | name          |
      | otherTestName |
    When the PATCH "/user/{randomId}" endpoint is called
    Then the service returns HTTP 404

  Scenario: Caller wants to fully replace a non-existent User by ID
    Given a random non-existent ID is generated
    And the caller has the given User:
      | name          | surname       |
      | otherTestName | otherSurname  |
    When the PUT "/user/{randomId}" endpoint is called
    Then the service returns HTTP 404

  Scenario: Caller wants to delete a non-existent User by ID
    Given a random non-existent ID is generated
    When the DELETE "/user/{randomId}" endpoint is called
    Then the service returns HTTP 404

  Scenario: Caller wants to fully replace a User, but has a required field as null
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the caller has the given User:
      | surname       |
      | otherSurname  |
    When the PUT "/user/{userId}" endpoint is called
    Then the service returns HTTP 400
    And the service returns a message with the validation errors
      | obj     | field | rejectedValue | message         |
      | userDTO | name  | null          | Cannot be blank |

  Scenario: PATCH preserves existing values when all DTO fields are null
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the caller has the given User:
      | name | surname |
      |      |         |
    When the PATCH "/user/{userId}" endpoint is called
    Then the service returns HTTP 204
    And the User still has all original values
