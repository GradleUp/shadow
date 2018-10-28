<template>
  <a v-bind:href="absolutePath"><slot/></a>
</template>

<script>
export default {
  data () {
    return {
        absolutePath: "http://imperceptiblethoughts.com/shadow-new-site/api/com/github/jengelman/gradle/plugins/shadow/" + this.path
    }
  }
}
</script>