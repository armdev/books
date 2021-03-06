<template>
  <div>
    <!-- Header for filter, search, list vs grid -->
    <ViewHeader theThing="My Books"
                :numberOfThings="getCurrentLength"
                :totalNumber="AllData.totalNumData"
                :showAsList="ViewState.viewAsList"
                @gridOn="showGrid"
                @listOn="showList"
                @searchString="searchStringUpdated"
                @filterString="filterStringUpdated"
                @grabAll="grabAll"
                >
    </ViewHeader>

    <div class="columns">
      <!-- left side column with tag filters -->
      <div class="column"
           style="width: 10%; border-right: solid lightgray 1px;">

        <aside class="menu">
          <p class="menu-label is-size-5 has-text-dark"
             style="margin-bottom: 2px;">
            Tags
          </p>

          <!-- list of tags -->
          <ul class="menu-list">
            <li v-for="current in TagJson"
                style="margin-left: -0.5em;">
              <a @click="setTagFilter(current.name)"
                v-bind:class="{ 'is-size-6': isSelected(current), highlighted: isSelected(current) }">
                <span>
                  {{ current.name }}
                </span>
              </a>
            </li>
          </ul>
          <!-- end of tag list -->
        </aside>

      </div> <!-- end filters -->

      <!-- right side with books -->
      <div class="column is-11">
        <!-- when 'books' changes, a watcher in bookslist and booksgrid updates their view -->
        <ShelfAsList v-if="ViewState.viewAsList"
                     v-bind:filter="filterBookString"
                     v-bind:tagFilter="ViewState.tagFilter"
                     v-bind:allTags="TagJson"
                     :userBooks="AllData.UserBooksJson"></ShelfAsList>
        <ShelfAsGrid v-else
                     v-bind:filter="filterBookString"
                     v-bind:tagFilter="ViewState.tagFilter"
                     v-bind:allTags="TagJson"
                     :userBooks="AllData.UserBooksJson"></ShelfAsGrid>

        <infinite-loading @infinite="infiniteHandler" ref="infiniteLoading">
          <span slot="no-more">
            -- end shelf ({{ AllData.UserBooksJson.length }})--
          </span>
          <span slot="no-results">
            -- no results --
          </span>
        </infinite-loading>

      </div>
    </div>

    <!-- error message to user -->
    <article v-if="errorMessage"
             class="main-width message is-danger">
      <div class="message-body">
        {{ errorMessage }}
      </div>
    </article>
  </div>
</template>

<script>
  import Auth from '../../auth'
  import ViewHeader from '../ViewHeader.vue'
  import ShelfAsList from './ShelfAsList.vue'
  import ShelfAsGrid from './ShelfAsGrid.vue'
  import InfiniteLoading from 'vue-infinite-loading'
  import _ from 'lodash'

  export default {
    // Components we depend on
    components: { ViewHeader, ShelfAsList, ShelfAsGrid, InfiniteLoading },
    // Data for this component
    data () {
      return {
        // Error message
        errorMessage: '',
        // string to filter books on
        filterBookString: '',
        // string to search/query books on
        searchBookString: '',
        // tags from server
        TagJson: [],
        /**
         * Contains the JSON for User Books
         * and the status of the infinite scrolling, e.g.
         * the current state of querying the server for data.
         */
        AllData: {
          // data from ajax
          UserBooksJson: [],
          // Index of data to query for
          dataStart: 0,
          // Length of data to get
          lengthToGet: 15,
          // Current end of our data length
          end: -1,
          // Total number of datum to get
          totalNumData: -1,
          // Reset the data
          reset: function () {
            this.UserBooksJson = []
            this.dataStart = 0
            this.end = -1
          }
        },
        /**
         * Current state of the view
         */
        ViewState: {
          valid: 'yes',
          // flag to determine if we present as list or grid
          viewAsList: true,
          // currently selected 'tag' filter
          tagFilter: ''
        }
      }
    },
    /**
     * When mounted, get the user books
     *
     */
    mounted: function () {
      // get user books from store
      let temp = this.$store.state.userBooks
      if (temp && temp.UserBooksJson) {
        this.AllData = temp
      }

      // Get list/grid status
      temp = this.$store.state.userBooksView
      if (temp && temp.valid) {
        this.ViewState = temp
      }

      this.getTags()
    },
    /**
     * Get length of the userbooks' json
     */
    computed: {
      getCurrentLength: function () {
        return this.AllData.UserBooksJson.length
      }
    },
    /**
     * Methods
     */
    methods: {
      /**
       * Is the user logged in?
       */
      isLoggedIn () {
        return Auth.isAuthenticated()
      },
      /**
       * Infinite loading. Get list of User Books
       */
      infiniteHandler ($state) {
        let self = this
        const authString = Auth.getAuthHeader()
        let params = {
          offset: self.AllData.dataStart,
          limit: self.AllData.lengthToGet
        }
        let url = '/user_book/' + Auth.user.id

        if (this.searchBookString !== '') {
          url = url + '?book_title=' + this.searchBookString
        }

        this.$axios.get(url, {
          headers: { Authorization: authString },
          params: params })
          .then((response) => {
            // Get data segment information
            let incomingData = response.data.data
            // start of data inside total set
            let start = response.data.offset
            // length of data in this response
            let length = response.data.limit
            // Total # of datum
            let totalSize = response.data.total

            // Has the dataset size changed?
            if (self.AllData.totalNumData >= 0 && totalSize !== self.AllData.totalNumData) {
              console.log('ViewShelf: Dataset length for userbooks has changed. Resetting.')

              self.AllData.reset()
              self.AllData.totalNumData = totalSize

              if ($state) {
                $state.reset()
              }
              return
            }

            // This method could be called when we come back to the tab
            // so check that we have all the data or not
            if (self.AllData.UserBooksJson.length >= totalSize) {
              if ($state) {
                $state.loaded()
                $state.complete()
              }
              return
            }

            // Data set is unchanged, continue getting data
            // save list of user books
            self.AllData.UserBooksJson = _.concat(self.AllData.UserBooksJson, incomingData)
            self.AllData.end = start + length
            self.AllData.dataStart = start + self.AllData.lengthToGet
            self.AllData.totalNumData = totalSize

            // save to $store
            this.$store.commit('setUserBooks', self.AllData)

            if ($state) {
              $state.loaded()
            }

            if (self.AllData.end >= self.AllData.totalNumData) {
              if ($state) {
                $state.complete()
              }
            }
          })
          .catch(function (error) {
            if ($state) {
              $state.complete()
            }
            if (error.response) {
              if (error.response.status === 401) {
                Event.$emit('got401')
              }
            } else {
              console.log(error)
            }
          })
      },
      /**
       * Get tags from database
       */
      getTags () {
        const authString = Auth.getAuthHeader()
        let self = this
        this.TagJson = {}
        this.$axios.get('/tag/', { headers: { Authorization: authString } })
          .then((response) => {
            self.TagJson = response.data.data
          })
          .catch(function (error) {
            if (error.response.status === 401) {
              Event.$emit('got401')
            } else {
              console.log(error)
            }
          })
      },
      /**
       * Set the tag filter
       */
      setTagFilter (newfilter) {
        if (this.ViewState.tagFilter === newfilter) {
          // Reset tag filter to empty
          this.ViewState.tagFilter = ''
        } else {
          this.ViewState.tagFilter = newfilter
        }
      },
      /**
       * Return true if the incoming tag is the same as the filter
       */
      isSelected (tag) {
        if (tag.name === this.ViewState.tagFilter) {
          return true
        } else {
          return false
        }
      },
      /**
       * Show the grid view
       */
      showGrid () {
        this.ViewState.viewAsList = false
        this.$store.commit('setUserBooksView', this.ViewState)
      },
      /**
       * Show the list view
       */
      showList () {
        this.ViewState.viewAsList = true
        this.$store.commit('setUserBooksView', this.ViewState)
      },
      /**
       * The search string has been updated
       */
      searchStringUpdated (value) {
        this.searchBookString = value
        this.AllData.reset()
        this.$refs.infiniteLoading.$emit('$InfiniteLoading:reset')
      },
      /**
       * The filter string has been updated
      */
      filterStringUpdated (value) {
        this.filterBookString = value
      },
      /**
       * Grab all values
       */
      grabAll () {
        this.AllData.lengthToGet = this.AllData.totalNumData - this.AllData.end
        this.$refs.infiniteLoading.$emit('$InfiniteLoading:reset')
        this.infiniteHandler(null)
      },
      /**
       * Print an Error to the user
       */
      printError (printThis) {
        let self = this
        this.errorMessage = printThis
        // Have the modal with errorMessage go away in bit
        setTimeout(function () {
          self.errorMessage = ''
        }, 2500)
      }
    }
  }
</script>

<style lang="css">
  .highlighted {
  background-color: lightgray;
  }
</style>
