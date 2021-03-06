package org.jetbrains.demo.thinkter

import io.mockk.*
import org.jetbrains.demo.thinkter.dao.ThinkterStorage
import org.jetbrains.demo.thinkter.model.UserThoughtsResponse
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.locations.Locations
import org.jetbrains.ktor.routing.HttpMethodRouteSelector
import org.jetbrains.ktor.routing.Routing
import org.junit.Before
import org.junit.Test

class UserPageKtTest {
    val route = mockk<Routing>()
    val dao = mockk<ThinkterStorage>()
    val hash = mockk<(String) -> String>()
    val locations = mockk<Locations>()

    val getUserThoughts = RouteBlockSlot()

    @Before
    fun setUp() {
        route.mockDsl(locations) {
            mockObj<UserThoughts> {
                mockSelect(HttpMethodRouteSelector(HttpMethod.Get)) {
                    captureBlock(getUserThoughts)
                }
            }
        }

        route.userPage(dao)
    }

    @Test
    fun testGetUserThoughtsOk() {
        getUserThoughts.invokeBlock(locations, UserThoughts("abcdef")) { handle ->
            mockHostReferrerHash(hash)
            mockUser(dao)
            mockGetThought(dao, 0)

            every { dao.userThoughts("abcdef") } returns listOf(1, 2, 3)

            coEvery { respond(any()) } just Runs

            handle()

            coVerify {
                respond(assert<UserThoughtsResponse> {
                    it.thoughts.any { it.id == 2 && it.text == "text" }
                })
            }
        }
    }

    @Test
    fun testGetUserThoughtsNoUserFound() {
        getUserThoughts.invokeBlock(locations, UserThoughts("abcdef")) { handle ->
            every { dao.user("abcdef") } returns null

            coEvery { respond(any<Any>()) } just Runs

            handle()

            coVerify {
                respond(HttpStatusCode.NotFound.description("User abcdef doesn't exist"))
            }
        }
    }

}