<template>
  <div>

    <p class="subtitle is-4"
       style="border-bottom: solid lightgray 1px;">
      Manage tags
    </p>


    <!-- Main container -->
    <nav class="level">
      <!-- Left side -->
      <div class="level-left">

        <div class="level-item">
          <p class="subtitle is-5">
            Add new tag: 
          </p>
        </div>

        <div class="level-item">
          <p class="control">
            <input class="input"
                   v-model="forminput.name"
                   @keyup.enter="addTag"
                   type="text"
                   placeholder="tag name">
          </p>
        </div>

        <!-- buttons -->
        <div class="level-item">
          <p class="control">
            <button class="button is-info"
                    @click="addTag">
              Create
            </button>
          </p>
          
          <p class="control">
            <button class="button is-light"
                    @click="clearInputs">
              Clear
            </button>
          </p>
        </div>
      </div>
    </nav>
    <!-- end of top container -->

    <br>

    <p class="subtitle is-4">
      <span style="border-bottom: solid gray 1px;">
        Tags
      </span>
    </p>

    <div class="field is-grouped is-grouped-multiline">

      <div v-for="current in tagJson"
           class="control">

        <!-- single tag -->
        <div class="tags has-addons">
          <span class="tag is-info">
            {{ current.name }}
          </span>
          <a class="tag is-delete"
             @click="tagWasDeleted(current)">
          </a>
        </div>
          <!-- end tag -->

      </div> <!-- end v-for -->
    </div>


    <!-- longer error message to user -->
    <article v-if="errorMessage"
             class="main-width message is-danger">
      <div class="message-body">
        {{ errorMessage }}
      </div>
    </article>

    <!-- Message  -->
    <div v-if="userMessage"
         class="animated fadeOutDown"
         style="font-size: 150%" >
      {{ userMessage }}
    </div>


  </div>
</template>

<script>
  import Auth from '../auth'

//  import UpdateDb from '../updatedb'

  export default {
    components: { },
    // Data
    data () {
      return {
        // tag JSON data
        tagJson: {},
        // Form inputs
        forminput: {
          name: ''
        },
        // message for user
        userMessage: '',
        // Error message
        errorMessage: ''
      }
    },
    /**
     * Called before this component would be loaded.   This halts the rendering if the current
     * user is not authenticated as being in the 'admin' group.
     */
    beforeRouteEnter (to, from, next) {
      if (Auth.isAuthenticated('admin')) {
        next()
      } else {
        next(false)
      }
    },
    /**
     * When mounted, get list of users from database
     */
    mounted: function () {
      this.getTags()
    },
    // Methods
    methods: {
      /**
       * Clear inputs/form
       */
      clearInputs () {
        this.forminput.name = ''
      },
      /**
       * Create a new tag
       *
       */
      addTag () {
        console.log('create tag: ' + this.forminput.name)

        const authString = Auth.getAuthHeader()
        let self = this
        let data = {
          name: this.forminput.name,
          data: ''
        }
        let url = '/tag'
        this.$axios.post(url, data, { headers: { Authorization: authString } })
          .then((response) => {
            console.log('tag ' + this.forminput.name + ' created')
            self.getTags()
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
       * A tag was deleted, remove from db
       *
       */
      tagWasDeleted (currentTag) {
        console.log('delete tag: ' + currentTag.name)

        const authString = Auth.getAuthHeader()
        let self = this
        let url = '/tag/' + currentTag.id
        this.$axios.delete(url, { headers: { Authorization: authString } })
          .then((response) => {
            console.log('tag deleted')
            self.getTags()
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
       * Get tags from database
       */
      getTags () {
        this.clearInputs()

        const authString = Auth.getAuthHeader()
        let self = this
        this.tagJson = {}
        this.$axios.get('/tag/', { headers: { Authorization: authString } })
          .then((response) => {
            self.tagJson = response.data.data
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
       * Print an Error to the user
       */
      printError (printThis) {
        let self = this
        this.errorMessage = printThis
        // Have the modal with userMessage go away in bit
        setTimeout(function () {
          self.errorMessage = ''
        }, 2500)
      },
      /**
       * Print a message to the user
       */
      printMessage (printThis) {
        let self = this
        self.userMessage = printThis
        // Have the modal with userMessage go away in 1 second
        setTimeout(function () {
          self.userMessage = ''
        }, 2000)
      }
    }
  }
</script> 

<style>

.fade-enter-active, .fade-leave-active {
  transition: opacity 5s
}
.fade-enter, .fade-leave-to /* .fade-leave-active below version 2.1.8 */ {
  opacity: 0
}

.fadeOutDown {
  animation-duration: 3s;
}
</style>
