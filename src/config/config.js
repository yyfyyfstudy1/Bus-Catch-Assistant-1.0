const config = {
  development: {
    apiBaseUrl: '/api',
    apiKey: process.env.VITE_API_KEY || ''
  },
  production: {
    apiBaseUrl: 'https://api.transport.nsw.gov.au',
    apiKey: process.env.VITE_API_KEY || ''
  }
}

export default {
  ...config[process.env.NODE_ENV || 'development']
} 