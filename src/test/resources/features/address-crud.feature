Feature: Address CRUD operations
  The CrudController and CrudService should be able to handle all the basic requests made to the test API

  Scenario: Caller wants to create a new Address
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the caller has the given Address:
      | address     |
      | testAddress |
    When the POST "/address" endpoint is called
    Then the service returns HTTP 201
    And the new Address was created

  Scenario: Caller wants to get an already existing Address by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    Given the given Address exists:
      | address     |
      | testAddress |
    When the GET "/address/{addressId}" endpoint is called
    Then the service returns HTTP 200
    And the service returns the Address

  Scenario: Caller wants to update an already existing Address by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the given Address exists:
      | address     |
      | testAddress |
    And the caller has just the given Address:
      | address       |
      | otherTestName |
    When the PATCH "/address/{addressId}" endpoint is called
    Then the service returns HTTP 204
    And the updated Address is found in the database with the values:
      | address       |
      | otherTestName |

  Scenario: Caller wants to update an already existing Address by ID with empty data
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the given Address exists:
      | address     |
      | testAddress |
    And the caller has an empty Address for some reason
    When the PATCH "/address/{addressId}" endpoint is called
    Then the service returns HTTP 204
    And the updated Address is found in the database with the values:
      | address     |
      | testAddress |

  Scenario: Caller wants to update an already existing Address by ID with another User ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the given Address exists:
      | address     |
      | testAddress |
    And the given User exists:
      | name      | surname  |
      | testUser2 | sursur   |
    And the caller has the given Address:
      | address       |
      | otherTestName |
    When the PATCH "/address/{addressId}" endpoint is called
    Then the service returns HTTP 204
    And the updated Address is found in the database with the values:
      | address       |
      | otherTestName |

  Scenario: Caller wants to delete a Address by ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    Given the given Address exists:
      | address     |
      | testAddress |
    When the DELETE "/address/{addressId}" endpoint is called
    Then the service returns HTTP 204
    And the Address was removed from the database

  Scenario: Caller wants to get all Address by User ID
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    Given the given Address exists:
      | address     |
      | testAddress |
    When the GET "/address/findAllBy/userId" endpoint is called:
      | type   | key      | value     |
      | param  | userId   | {userId}  |
    And the service returns HTTP 200
    And the service returns a page with the matching Addresses
