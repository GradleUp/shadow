<template>
  <a v-bind:href="absolutePath"><slot/></a>
</template>

<script>
export default {
  data () {
    return {
        absolutePath: "https://gradleup.com/shadow/api/com/github/jengelman/gradle/plugins/shadow/" + this.path
    }
  }
}
</script>