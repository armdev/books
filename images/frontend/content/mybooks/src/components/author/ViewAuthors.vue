<template>
  <!-- Main container -->
  <div>

    <!-- Header for filter, search, list vs grid -->
    <ViewHeader theThing="Authors"
                :numberOfThings="lengthOfViewableAuthors"
                @gridOn="showGrid"
                @listOn="showList"
                @searchString="searchStringUpdated"
                @filterString="filterStringUpdated"
                >
    </ViewHeader>

    <authorslist v-if="viewAsList" 
                 :authors="authorList"></authorslist>
    <authorsgrid v-else 
                 :authors="authorList"></authorsgrid>


    <infinite-loading @infinite="infiniteHandler">
      <span slot="no-more">
        -- end authors ({{ lengthOfViewableAuthors }}) --
      </span>
      <span slot="no-results">
        -- no results --
      </span>
    </infinite-loading>


  </div>
</template>


<script>
  import Auth from '../../auth'
  import authorslist from './AuthorsAsList.vue'
  import authorsgrid from './AuthorsAsGrid.vue'
  import ViewHeader from '../ViewHeader.vue'
  import InfiniteLoading from 'vue-infinite-loading'
  import _ from 'lodash'

 /**
  * Default data
  */
  export default {
    // Components we depend on
    components: { authorslist, authorsgrid, ViewHeader, InfiniteLoading },
    // Data for this component
    data () {
      return {
        // flag to determine if we present as list or grid
        viewAsList: true,
        // string to search/query authors on
        searchAuthorString: '',
        // string to filter on
        filterAuthorString: '',
        // length of viewable authors
        lengthOfViewableAuthors: 0,
        /**
         * Contains the JSON for Authors
         * and the status of the infinite scrolling, e.g.
         * the current state of querying the server for data.
         */
        AllData: {
          // data from ajax
          AuthorsJson: [],
          // Index of data to query for
          dataStart: 0,
          // Length of data to get
          lengthToGet: 15,
          // Current end of our data length
          end: -1,
          // Total number of datum to get
          totalNumData: 0
        }
      }
    },
    /**
     * When mounted, get the stored data
     *
     */
    mounted: function () {
      // get authros from store
      let temp = this.$store.state.allAuthors
      if (temp && temp.AuthorsJson) {
        this.AllData = temp
      }
    },
    /**
     * Methods
     */
    methods: {
      /**
       * Infinite loading. Get the list of authors
       */
      infiniteHandler ($state) {
        let self = this

        const authString = Auth.getAuthHeader()
        this.$axios.get('/author', { headers: { Authorization: authString }, params: { start: self.AllData.dataStart, segmentSize: self.AllData.lengthToGet } })
          .then((response) => {
            let incomingData = response.data.data
            let start = response.data.start
            let length = response.data.length
            self.AllData.AuthorsJson = _.concat(self.AllData.AuthorsJson, incomingData)

            $state.loaded()

            // Update our pointers (start, end, so on
            self.AllData.totalNumData = response.data.totalFound
            self.AllData.end = start + length
            self.AllData.dataStart = start + self.AllData.lengthToGet

            if (self.AllData.end >= self.AllData.totalNumData) {
              $state.complete()
            }

            // save to $store
            this.$store.commit('setAllAuthors', self.AllData)
          })
          .catch(function (error) {
            $state.complete()
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
       * Show the grid view
       */
      showGrid () {
        this.viewAsList = false
      },
      /**
       * Show the list view
       */
      showList () {
        this.viewAsList = true
      },
      /**
       * The search string has been updated
       */
      searchStringUpdated (value) {
        this.searchAuthorString = value
      },
      /**
       * The filter string has been updated
      */
      filterStringUpdated (value) {
        this.filterAuthorString = value
      }
    },
    computed: {
      authorList: function () {
        let result = this.AllData.AuthorsJson
        this.lengthOfViewableAuthors = result.length

        if (!this.filterAuthorString) {
          return result
        }

        const filterValue = this.filterAuthorString.toLowerCase()

        const filter = event =>
          event.name.toLowerCase().includes(filterValue)

        let filteredResult = result.filter(filter)
        this.lengthOfViewableAuthors = filteredResult.length
        return filteredResult
      }
    }
  }
</script>

<style>
a:hover {
    color: black;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 5s
}

.main-width {
  width: 65%
}

.author {
   cursor: pointer
}


</style>