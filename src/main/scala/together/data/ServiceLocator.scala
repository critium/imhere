package together.data

import together.audio._

object ServiceLocator {
  val dataService = new DataServiceImpl
  val channelService = new ChannelServiceImpl(false)
  val orchestrationService = new OrchestrationServiceImpl(channelService, dataService)
}
