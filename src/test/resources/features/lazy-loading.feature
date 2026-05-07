Feature: Lazy loading within transactions
  CrudService operations run inside explicit transactions so that Mapper can traverse
  lazy-loaded JPA relationships.

  User.addresses is @OneToMany (default LAZY) backed by a Hibernate PersistentSet.
  UserDetailResponse includes an addresses field that Mapper populates by traversing
  the lazy collection. Without an active session this collection cannot initialize.

  Scenario: GET User detail includes lazy-loaded addresses collection
    Given the given User exists:
      | name     | surname  |
      | testUser | sursur   |
    And the given Address exists:
      | address     |
      | testAddress |
    When the GET "/user-detail/{userId}" endpoint is called
    Then the service returns HTTP 200
    And the User detail response includes the addresses collection
